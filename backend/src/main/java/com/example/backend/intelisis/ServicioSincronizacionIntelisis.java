package com.example.backend.intelisis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copia en segundo plano los datos de Intelisis a las tablas espejo de HAERO
 * (V15): catalogo de medicamentos, proveedores, existencia por lote y
 * recepciones de proveedor con sus lotes.
 *
 * Pensado para que HAERO siga rapido:
 *  - Las pantallas nunca consultan Intelisis; leen el espejo.
 *  - Cada entidad se lee con UNA consulta por conjunto (sin un viaje por
 *    renglon) y sin bloquear al ERP (READ UNCOMMITTED).
 *  - Primero se lee todo de Intelisis y despues se reemplaza el espejo en una
 *    sola transaccion corta con inserciones por lotes de 1000. Si la lectura
 *    falla, el espejo anterior queda intacto.
 *  - Nunca corren dos sincronizaciones a la vez.
 */
@Service
public class ServicioSincronizacionIntelisis {

    private static final Logger log = LoggerFactory.getLogger(ServicioSincronizacionIntelisis.class);
    private static final int TAMANO_LOTE_INSERCION = 1000;
    private static final String MOV_RECEPCION = "Ent. Compra Farmacia";

    private final ConexionIntelisis conexion;
    private final JdbcTemplate haero;
    private final TransactionTemplate transaccion;
    private final List<String> almacenes;
    private final int diasRecepciones;

    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final ExecutorService ejecutorManual = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "intelisis-sincronizacion-manual");
        hilo.setDaemon(true);
        return hilo;
    });

    public ServicioSincronizacionIntelisis(ConexionIntelisis conexion,
                                           JdbcTemplate haero,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${intelisis.almacenes:25301,25301-C}") String almacenes,
                                           @Value("${intelisis.recepciones.dias:400}") int diasRecepciones) {
        this.conexion = conexion;
        this.haero = haero;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.almacenes = Arrays.stream(almacenes.split(",")).map(String::trim).filter(a -> !a.isEmpty()).toList();
        this.diasRecepciones = diasRecepciones;
    }

    public boolean estaEnCurso() {
        return enCurso.get();
    }

    /** Respaldo programado; por defecto cada 15 minutos, un minuto despues de arrancar. */
    @Scheduled(fixedDelayString = "${intelisis.sincronizacion.intervalo:PT15M}",
               initialDelayString = "${intelisis.sincronizacion.retraso-inicial:PT1M}")
    public void sincronizarProgramada() {
        if (conexion.disponible()) {
            sincronizar("AUTOMATICA");
        }
    }

    /**
     * Lanza una sincronizacion en segundo plano (boton "Sincronizar ahora").
     * Regresa false si ya hay una en curso.
     */
    public boolean lanzarManual() {
        if (!conexion.disponible()) {
            throw new IllegalStateException(conexion.motivoNoDisponible());
        }
        if (enCurso.get()) {
            return false;
        }
        ejecutorManual.submit(() -> sincronizar("MANUAL"));
        return true;
    }

    void sincronizar(String origen) {
        if (!enCurso.compareAndSet(false, true)) {
            return;
        }
        long inicio = System.currentTimeMillis();
        Long bitacoraId = null;
        try {
            bitacoraId = abrirBitacora(origen);
            JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new IllegalStateException(conexion.motivoNoDisponible()));
            LocalDate desde = LocalDate.now().minusDays(diasRecepciones);

            // 1) Leer todo de Intelisis (fuera de cualquier transaccion de HAERO).
            Collection<Object[]> existencias = leerExistencias(intelisis);
            Collection<Object[]> articulos = leerArticulos(intelisis);
            Collection<Object[]> proveedores = leerProveedores(intelisis);
            Collection<Object[]> recepciones = leerRecepciones(intelisis, desde);
            Collection<Object[]> lotesRecepcion = leerLotesRecepcion(intelisis, desde);

            // 2) Reemplazar el espejo de golpe.
            transaccion.executeWithoutResult(estado -> {
                reemplazar("intelisis_articulo",
                        "INSERT INTO intelisis_articulo (articulo, clave_base, descripcion, unidad, unidad_compra, unidad_cantidad, grupo, estatus) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        articulos);
                reemplazar("intelisis_proveedor",
                        "INSERT INTO intelisis_proveedor (proveedor, nombre, rfc, estatus) VALUES (?, ?, ?, ?)",
                        proveedores);
                reemplazar("intelisis_existencia_lote",
                        "INSERT INTO intelisis_existencia_lote (almacen, articulo, clave_base, serie_lote, existencia, fecha_caducidad, ultima_entrada, ultima_salida) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        existencias);
                reemplazar("intelisis_recepcion_lote",
                        "INSERT INTO intelisis_recepcion_lote (compra_id, articulo, serie_lote, cantidad, fecha_caducidad) VALUES (?, ?, ?, ?, ?)",
                        lotesRecepcion);
                reemplazar("intelisis_recepcion",
                        "INSERT INTO intelisis_recepcion (compra_id, mov_id, fecha_emision, almacen, proveedor, origen, origen_id, contrato_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        recepciones);
            });

            long duracion = System.currentTimeMillis() - inicio;
            haero.update("UPDATE intelisis_sincronizacion SET fin = ?, estatus = 'OK', articulos = ?, proveedores = ?, lotes_existencia = ?, "
                            + "recepciones = ?, lotes_recepcion = ?, duracion_ms = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), articulos.size(), proveedores.size(), existencias.size(),
                    recepciones.size(), lotesRecepcion.size(), duracion, bitacoraId);
            log.info("Sincronización Intelisis ({}) OK en {} ms: {} artículos, {} lotes con existencia, {} recepciones.",
                    origen, duracion, articulos.size(), existencias.size(), recepciones.size());
        } catch (Exception e) {
            log.warn("Sincronización Intelisis ({}) falló: {}", origen, mensaje(e));
            if (bitacoraId != null) {
                try {
                    haero.update("UPDATE intelisis_sincronizacion SET fin = ?, estatus = 'ERROR', duracion_ms = ?, error = ? WHERE id = ?",
                            Timestamp.valueOf(LocalDateTime.now()), System.currentTimeMillis() - inicio, recortar(mensaje(e), 1000), bitacoraId);
                } catch (Exception errorBitacora) {
                    log.warn("No se pudo registrar el error en la bitácora: {}", errorBitacora.getMessage());
                }
            }
        } finally {
            enCurso.set(false);
        }
    }

    // ---------- Lectura de Intelisis (solo SELECT) ----------

    private Collection<Object[]> leerExistencias(JdbcTemplate intelisis) {
        String sql = "SELECT s.Almacen, s.Articulo, s.SerieLote, SUM(s.Existencia) AS existencia, MIN(s.FechaCaducidad) AS caducidad, "
                + "MAX(s.UltimaEntrada) AS ultima_entrada, MAX(s.UltimaSalida) AS ultima_salida "
                + "FROM SerieLote s WITH (NOLOCK) "
                + "WHERE s.Almacen IN (" + marcadores(almacenes.size()) + ") AND s.Existencia > 0 "
                + "GROUP BY s.Almacen, s.Articulo, s.SerieLote";

        // Llave limpia (sin espacios ni tabuladores sobrantes): dos renglones que solo
        // difieren en eso se suman en uno.
        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            String almacen = texto(rs, 1, 10);
            String articulo = texto(rs, 2, 20);
            String lote = texto(rs, 3, 50);
            if (almacen == null || articulo == null || lote == null) {
                return;
            }
            BigDecimal existencia = rs.getBigDecimal(4);
            Object[] previa = filas.get(almacen + "|" + articulo + "|" + lote);
            if (previa != null) {
                previa[4] = ((BigDecimal) previa[4]).add(existencia);
                return;
            }
            filas.put(almacen + "|" + articulo + "|" + lote, new Object[]{
                    almacen, articulo, claveBase(articulo), lote, existencia,
                    fecha(rs, 5), fecha(rs, 6), fecha(rs, 7)});
        }, almacenes.toArray());
        return filas.values();
    }

    private Collection<Object[]> leerArticulos(JdbcTemplate intelisis) {
        // Medicamentos (claves 010/040, con o sin G- y -N) mas cualquier articulo con
        // existencia en los almacenes sincronizados.
        String sql = "SELECT a.Articulo, a.Descripcion1, a.Unidad, a.UnidadCompra, a.UnidadCantidad, a.Grupo, a.Estatus "
                + "FROM Art a WITH (NOLOCK) "
                + "WHERE a.Articulo LIKE '010.000.%' OR a.Articulo LIKE '040.000.%' OR a.Articulo LIKE 'G-0[14]0.000.%' "
                + "   OR a.Articulo IN (SELECT s.Articulo FROM SerieLote s WITH (NOLOCK) "
                + "                     WHERE s.Almacen IN (" + marcadores(almacenes.size()) + ") AND s.Existencia > 0)";

        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            String articulo = texto(rs, 1, 20);
            if (articulo == null || filas.containsKey(articulo)) {
                return;
            }
            double unidadCantidad = rs.getDouble(5);
            filas.put(articulo, new Object[]{
                    articulo, claveBase(articulo), texto(rs, 2, 500), texto(rs, 3, 50), texto(rs, 4, 50),
                    rs.wasNull() ? null : unidadCantidad, texto(rs, 6, 50), texto(rs, 7, 15)});
        }, almacenes.toArray());
        return filas.values();
    }

    private Collection<Object[]> leerProveedores(JdbcTemplate intelisis) {
        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query("SELECT p.Proveedor, p.Nombre, p.RFC, p.Estatus FROM Prov p WITH (NOLOCK)", rs -> {
            String proveedor = texto(rs, 1, 20);
            if (proveedor != null && !filas.containsKey(proveedor)) {
                filas.put(proveedor, new Object[]{proveedor, texto(rs, 2, 150), texto(rs, 3, 20), texto(rs, 4, 15)});
            }
        });
        return filas.values();
    }

    private String filtroRecepciones() {
        return "c.Mov = '" + MOV_RECEPCION + "' AND c.Estatus = 'CONCLUIDO' "
                + "AND c.Almacen IN (" + marcadores(almacenes.size()) + ") AND c.FechaEmision >= ?";
    }

    private Object[] parametrosRecepciones(LocalDate desde) {
        List<Object> parametros = new ArrayList<>(almacenes);
        parametros.add(Date.valueOf(desde));
        return parametros.toArray();
    }

    private Collection<Object[]> leerRecepciones(JdbcTemplate intelisis, LocalDate desde) {
        String sql = "SELECT c.ID, c.MovID, c.FechaEmision, c.Almacen, c.Proveedor, c.Origen, c.OrigenID, c.ContratoID "
                + "FROM Compra c WITH (NOLOCK) WHERE " + filtroRecepciones();

        Map<Integer, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            filas.putIfAbsent(id, new Object[]{
                    id, texto(rs, 2, 20), fecha(rs, 3), texto(rs, 4, 10), texto(rs, 5, 20),
                    texto(rs, 6, 30), texto(rs, 7, 20), entero(texto(rs, 8, 20))});
        }, parametrosRecepciones(desde));
        return filas.values();
    }

    private Collection<Object[]> leerLotesRecepcion(JdbcTemplate intelisis, LocalDate desde) {
        String sql = "SELECT m.ID, m.Articulo, m.SerieLote, SUM(m.Cantidad) AS cantidad, MIN(m.FechaCaducidad) AS caducidad "
                + "FROM SerieLoteMov m WITH (NOLOCK) "
                + "JOIN Compra c WITH (NOLOCK) ON c.ID = m.ID "
                + "WHERE m.Modulo = 'COMS' AND " + filtroRecepciones() + " "
                + "GROUP BY m.ID, m.Articulo, m.SerieLote";

        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String articulo = texto(rs, 2, 20);
            String lote = texto(rs, 3, 50);
            if (articulo == null || lote == null) {
                return;
            }
            BigDecimal cantidad = rs.getBigDecimal(4);
            String llave = id + "|" + articulo + "|" + lote;
            Object[] previa = filas.get(llave);
            if (previa != null) {
                previa[3] = ((BigDecimal) previa[3]).add(cantidad);
                return;
            }
            filas.put(llave, new Object[]{id, articulo, lote, cantidad, fecha(rs, 5)});
        }, parametrosRecepciones(desde));
        return filas.values();
    }

    // ---------- Escritura en HAERO ----------

    private void reemplazar(String tabla, String insercion, Collection<Object[]> filas) {
        haero.update("DELETE FROM " + tabla);
        List<Object[]> lista = new ArrayList<>(filas);
        for (int i = 0; i < lista.size(); i += TAMANO_LOTE_INSERCION) {
            haero.batchUpdate(insercion, lista.subList(i, Math.min(i + TAMANO_LOTE_INSERCION, lista.size())));
        }
    }

    private Long abrirBitacora(String origen) {
        return haero.queryForObject(
                "INSERT INTO intelisis_sincronizacion (inicio, estatus, origen) OUTPUT INSERTED.id VALUES (?, 'EN_CURSO', ?)",
                Long.class, Timestamp.valueOf(LocalDateTime.now()), origen);
    }

    // ---------- Utilidades ----------

    /** Clave sin el sufijo "-N" de Intelisis: 010.000.0101.00-1 -> 010.000.0101.00. */
    static String claveBase(String articulo) {
        return articulo.replaceFirst("-\\d+$", "");
    }

    private static String texto(ResultSet rs, int columna, int maximo) throws SQLException {
        String valor = rs.getString(columna);
        if (valor == null) {
            return null;
        }
        valor = valor.strip();
        return valor.isEmpty() ? null : recortar(valor, maximo);
    }

    private static Date fecha(ResultSet rs, int columna) throws SQLException {
        Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : Date.valueOf(valor.toLocalDateTime().toLocalDate());
    }

    private static Integer entero(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String marcadores(int cantidad) {
        return String.join(", ", Collections.nCopies(cantidad, "?"));
    }

    private static String recortar(String valor, int maximo) {
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private static String mensaje(Throwable e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName();
    }
}
