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
    // El primero de la lista (25301): el almacen de medicamentos, donde llegan los pedidos.
    private final String almacenPrincipal;
    private final int diasRecepciones;
    // Movimientos de Inv que tambien son entradas al almacen principal (donaciones y traspasos).
    private final List<String> movimientosEntradaInv;
    // "Consumo Farmacia" y "Devolucion Farmacia": la baja por volumen de Farmacia (HU32).
    private final List<String> movimientosFarmacia;
    private final List<String> movimientosConsumo;
    private final int diasMovimientosFarmacia;

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
                                           @Value("${intelisis.recepciones.dias:400}") int diasRecepciones,
                                           @Value("${intelisis.entradas.movimientos-inv:Donacion FONSABI,Donacion Farmacia,IMSS EntradaTraspaso}")
                                           String movimientosEntradaInv,
                                           @Value("${intelisis.farmacia.movimientos-consumo:Consumo Farmacia}") String movimientosConsumoFarmacia,
                                           @Value("${intelisis.farmacia.movimientos-devolucion:Devolucion Farmacia}") String movimientosDevolucionFarmacia,
                                           @Value("${intelisis.farmacia.dias-verificacion:60}") int diasMovimientosFarmacia) {
        this.conexion = conexion;
        this.haero = haero;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.almacenes = Arrays.stream(almacenes.split(",")).map(String::trim).filter(a -> !a.isEmpty()).toList();
        this.almacenPrincipal = this.almacenes.get(0);
        this.diasRecepciones = diasRecepciones;
        this.movimientosEntradaInv = Arrays.stream(movimientosEntradaInv.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
        this.movimientosFarmacia = Arrays.stream((movimientosConsumoFarmacia + "," + movimientosDevolucionFarmacia).split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
        this.movimientosConsumo = Arrays.stream(movimientosConsumoFarmacia.split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
        this.diasMovimientosFarmacia = diasMovimientosFarmacia;
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
            Collection<Object[]> catalogo = leerCatalogo(intelisis);
            Collection<Object[]> factores = leerFactores(intelisis, catalogo);
            Collection<Object[]> pedidos = leerPedidos(intelisis, desde);
            Collection<Object[]> partidasPedido = leerPartidasPedido(intelisis, desde);
            Collection<Object[]> entradasInv = leerEntradasInv(intelisis, desde);
            Collection<Object[]> lotesEntradaInv = leerLotesEntradasInv(intelisis, desde);
            Collection<Object[]> movimientosFarmacia = leerMovimientosFarmacia(intelisis, LocalDate.now().minusDays(diasMovimientosFarmacia));
            Collection<Object[]> consumoMensual = leerConsumoMensual(intelisis);

            // Candado: si Intelisis entrega mucho menos que la vez anterior (por ejemplo,
            // durante un mantenimiento), no se toca nada.
            revisarQueNoVengaIncompleto("lotes con existencia", existencias.size(), "lotes_existencia");
            revisarQueNoVengaIncompleto("medicamentos", catalogo.size(), "medicamentos");

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
                reemplazar("intelisis_medicamento",
                        "INSERT INTO intelisis_medicamento (clave, articulo, nombre_generico, presentacion, forma_farmaceutica, concentracion, "
                                + "grupo_terapeutico, cuadro_basico, estatus, clave_cucop, unidad_compra, unidad_minima, riesgo, "
                                + "principal_indicacion, contraindicaciones, descripcion_completa, unidades_envase) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        catalogo);
                reemplazar("intelisis_factor",
                        "INSERT INTO intelisis_factor (clave, piezas_por_caja, origen, conversiones) VALUES (?, ?, ?, ?)",
                        factores);
                reemplazar("intelisis_pedido_partida",
                        "INSERT INTO intelisis_pedido_partida (compra_id, clave, cucop, cantidad, pendiente, cancelada) VALUES (?, ?, ?, ?, ?, ?)",
                        partidasPedido);
                reemplazar("intelisis_pedido",
                        "INSERT INTO intelisis_pedido (compra_id, mov_id, estatus, fecha_emision, fecha_entrega, fecha_cancelacion, proveedor, "
                                + "contrato_id, contrato, referencia) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        pedidos);
                reemplazar("intelisis_entrada_inv_lote",
                        "INSERT INTO intelisis_entrada_inv_lote (inv_id, articulo, clave_base, serie_lote, cantidad, fecha_caducidad) VALUES (?, ?, ?, ?, ?, ?)",
                        lotesEntradaInv);
                reemplazar("intelisis_entrada_inv",
                        "INSERT INTO intelisis_entrada_inv (inv_id, mov, mov_id, fecha_emision, proveedor, referencia, concepto) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        entradasInv);
                reemplazar("intelisis_mov_farmacia_lote",
                        "INSERT INTO intelisis_mov_farmacia_lote (inv_id, mov, mov_id, fecha_emision, articulo, clave_base, serie_lote, cantidad) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        movimientosFarmacia);
                reemplazar("intelisis_consumo_mes",
                        "INSERT INTO intelisis_consumo_mes (clave_base, mes, cajas, piezas) VALUES (?, ?, ?, ?)",
                        consumoMensual);
                actualizarCatalogoHaero();
                actualizarPiezasPorCaja();
                actualizarPedidosHaero();
                actualizarAvisosLotes();
            });

            long duracion = System.currentTimeMillis() - inicio;
            haero.update("UPDATE intelisis_sincronizacion SET fin = ?, estatus = 'OK', articulos = ?, proveedores = ?, lotes_existencia = ?, "
                            + "recepciones = ?, lotes_recepcion = ?, medicamentos = ?, pedidos = ?, entradas_inv = ?, duracion_ms = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), articulos.size(), proveedores.size(), existencias.size(),
                    recepciones.size(), lotesRecepcion.size(), catalogo.size(), pedidos.size(), entradasInv.size(), duracion, bitacoraId);
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

    /**
     * Catalogo de Farmacia: las claves "-1" de Art (sin las G- de Farmacia
     * Gratuita), con la ficha de ArtMedicamento cuando existe; si no, los datos
     * de Art. La clave de HAERO es la de Intelisis sin el "-1".
     */
    private Collection<Object[]> leerCatalogo(JdbcTemplate intelisis) {
        String sql = """
                SELECT x.Articulo,
                       COALESCE(NULLIF(LTRIM(m.NombreGenerico), ''), x.Descripcion1, b.Descripcion1) AS nombre,
                       COALESCE(NULLIF(LTRIM(m.Presentacion), ''), NULLIF(LTRIM(b.Presentacion), ''), NULLIF(LTRIM(x.Presentacion), '')) AS presentacion,
                       m.FormaFarmaceutica, m.Concentracion,
                       COALESCE(NULLIF(LTRIM(m.GrupoTerapeutico), ''), NULLIF(LTRIM(x.Grupo), ''), b.Grupo) AS grupo,
                       m.Cuadrobasico, x.Estatus,
                       COALESCE(NULLIF(LTRIM(x.CveCucop), ''), b.CveCucop) AS cucop,
                       COALESCE(NULLIF(LTRIM(b.UnidadCompra), ''), x.UnidadCompra) AS unidad_compra,
                       COALESCE(NULLIF(LTRIM(m.UnidadMedida), ''), x.UnidadMinimaConsumo) AS unidad_minima,
                       m.Riesgo, m.PrincipalIndicacion, m.Contraindicaciones,
                       COALESCE(x.Descripcion1, b.Descripcion1) AS descripcion_completa,
                       m.UnidadesEnvase
                FROM Art x WITH (NOLOCK)
                CROSS APPLY (SELECT LEFT(RTRIM(REPLACE(x.Articulo, CHAR(9), '')), LEN(RTRIM(REPLACE(x.Articulo, CHAR(9), ''))) - 2) AS clave) k
                LEFT JOIN Art b WITH (NOLOCK) ON b.Articulo = k.clave
                -- La ficha se busca por su clave y, si no, por la clave de Cuadro Basico que trae.
                OUTER APPLY (SELECT TOP 1 am.* FROM ArtMedicamento am WITH (NOLOCK)
                             WHERE am.Articulo = k.clave OR am.CuadrobasicoClave = k.clave
                             ORDER BY CASE WHEN am.Articulo = k.clave THEN 0 ELSE 1 END) m
                WHERE RTRIM(REPLACE(x.Articulo, CHAR(9), '')) LIKE '0[14]0.000.%-1'
                """;

        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            String articulo = texto(rs, 1, 20);
            String nombre = texto(rs, 2, 1000);
            if (articulo == null || nombre == null) {
                return;
            }
            String clave = recortar(claveBase(articulo), 30);
            if (filas.containsKey(clave)) {
                return;
            }
            boolean cuadroBasico = rs.getBoolean(7);
            Boolean cuadroBasicoONull = rs.wasNull() ? null : cuadroBasico;
            String estatus = texto(rs, 8, 15);
            String presentacion = texto(rs, 3, 1000);
            filas.put(clave, new Object[]{
                    clave, articulo, nombre, presentacion == null ? "" : presentacion,
                    texto(rs, 4, 200), texto(rs, 5, 2000), texto(rs, 6, 100), cuadroBasicoONull,
                    estatus == null ? null : estatus.toUpperCase(), texto(rs, 9, 20), texto(rs, 10, 50), texto(rs, 11, 50),
                    texto(rs, 12, 30), texto(rs, 13, 1000), texto(rs, 14, 1000), texto(rs, 15, 255), texto(rs, 16, 20)});
        });
        return filas.values();
    }

    /**
     * Piezas por caja de cada clave. Sale de los movimientos "Conversion" (cajas de la
     * clave base -> piezas de la clave -1): se toma el factor que mas se repite. Si la
     * clave no tiene conversiones, se usa UnidadesEnvase de la ficha; si tampoco, 1
     * (SIN_DATO, queda por revisar).
     */
    private Collection<Object[]> leerFactores(JdbcTemplate intelisis, Collection<Object[]> catalogo) {
        String sql = """
                WITH c AS (
                    SELECT d.ID, RTRIM(REPLACE(d.Articulo, CHAR(9), '')) AS articulo, SUM(d.Cantidad) AS q
                    FROM Inv i WITH (NOLOCK)
                    JOIN InvD d WITH (NOLOCK) ON d.ID = i.ID
                    WHERE i.Mov = 'Conversion' AND i.Estatus = 'CONCLUIDO' AND i.FechaEmision >= DATEADD(year, -3, GETDATE())
                    GROUP BY d.ID, RTRIM(REPLACE(d.Articulo, CHAR(9), ''))
                )
                SELECT b.articulo AS clave, ROUND(ABS(x.q / b.q), 4) AS factor, COUNT(*) AS veces
                FROM c x
                JOIN c b ON b.ID = x.ID AND x.articulo = b.articulo + '-1'
                WHERE b.q <> 0 AND x.q <> 0
                GROUP BY b.articulo, ROUND(ABS(x.q / b.q), 4)
                """;

        Map<String, Object[]> porClave = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            String clave = texto(rs, 1, 30);
            BigDecimal factor = rs.getBigDecimal(2);
            int veces = rs.getInt(3);
            if (clave == null || factor == null || factor.signum() <= 0) {
                return;
            }
            Object[] previo = porClave.get(clave);
            if (previo == null) {
                porClave.put(clave, new Object[]{clave, factor, "CONVERSION", veces, veces});
            } else {
                previo[3] = (Integer) previo[3] + veces;
                if (veces > (Integer) previo[4]) {
                    previo[1] = factor;
                    previo[4] = veces;
                }
            }
        });

        // Las claves del catalogo sin conversiones: piezas del envase de la ficha, o 1.
        for (Object[] medicamento : catalogo) {
            String clave = (String) medicamento[0];
            if (!porClave.containsKey(clave)) {
                BigDecimal deFicha = numeroPositivo((String) medicamento[16]);
                porClave.put(clave, deFicha != null
                        ? new Object[]{clave, deFicha, "FICHA", 0, 0}
                        : new Object[]{clave, BigDecimal.ONE, "SIN_DATO", 0, 0});
            }
        }
        List<Object[]> filas = new ArrayList<>();
        for (Object[] f : porClave.values()) {
            filas.add(new Object[]{f[0], f[1], f[2], f[3]});
        }
        return filas;
    }

    /** Pedidos a proveedor del almacen principal (pendientes, concluidos y cancelados) del periodo. */
    private Collection<Object[]> leerPedidos(JdbcTemplate intelisis, LocalDate desde) {
        String sql = "SELECT c.ID, c.MovID, c.Estatus, c.FechaEmision, c.FechaEntrega, c.FechaCancelacion, c.Proveedor, c.ContratoID, "
                + "c.NoContratoInterno, c.Referencia "
                + "FROM Compra c WITH (NOLOCK) "
                + "WHERE c.Mov = 'Pedido Proveedor' AND c.Almacen = ? AND c.Estatus IN ('PENDIENTE', 'CONCLUIDO', 'CANCELADO') "
                + "AND c.FechaEmision >= ?";
        Map<Integer, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String movId = texto(rs, 2, 20);
            if (movId == null) {
                return;
            }
            filas.putIfAbsent(id, new Object[]{
                    id, movId, texto(rs, 3, 15), fecha(rs, 4), fecha(rs, 5), rs.getTimestamp(6),
                    texto(rs, 7, 20), entero(texto(rs, 8, 20)), texto(rs, 9, 60), texto(rs, 10, 100)});
        }, almacenPrincipal, Date.valueOf(desde));
        return filas.values();
    }

    /** Partidas de medicamento de esos pedidos, una por clave (cantidades en cajas). */
    private Collection<Object[]> leerPartidasPedido(JdbcTemplate intelisis, LocalDate desde) {
        String sql = """
                SELECT d.ID, RTRIM(REPLACE(d.Articulo, CHAR(9), '')) AS clave, MAX(d.CveCucop) AS cucop,
                       SUM(d.Cantidad) AS cantidad, SUM(ISNULL(d.CantidadPendiente, 0)) AS pendiente,
                       SUM(ISNULL(d.CantidadCancelada, 0)) AS cancelada
                FROM Compra c WITH (NOLOCK)
                JOIN CompraD d WITH (NOLOCK) ON d.ID = c.ID
                WHERE c.Mov = 'Pedido Proveedor' AND c.Almacen = ? AND c.Estatus IN ('PENDIENTE', 'CONCLUIDO', 'CANCELADO')
                  AND c.FechaEmision >= ? AND d.Articulo LIKE '0[14]0.000.%'
                GROUP BY d.ID, RTRIM(REPLACE(d.Articulo, CHAR(9), ''))
                """;
        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String clave = texto(rs, 2, 30);
            if (clave == null) {
                return;
            }
            filas.putIfAbsent(id + "|" + clave, new Object[]{
                    id, clave, texto(rs, 3, 20), cero(rs.getBigDecimal(4)), cero(rs.getBigDecimal(5)), cero(rs.getBigDecimal(6))});
        }, almacenPrincipal, Date.valueOf(desde));
        return filas.values();
    }

    private String filtroEntradasInv() {
        return "i.Almacen = ? AND i.Estatus = 'CONCLUIDO' AND i.FechaEmision >= ? "
                + "AND i.Mov IN (" + marcadores(movimientosEntradaInv.size()) + ")";
    }

    private Object[] parametrosEntradasInv(LocalDate desde) {
        List<Object> parametros = new ArrayList<>();
        parametros.add(almacenPrincipal);
        parametros.add(Date.valueOf(desde));
        parametros.addAll(movimientosEntradaInv);
        return parametros.toArray();
    }

    /** Donaciones y traspasos que entraron al almacen principal en el periodo. */
    private Collection<Object[]> leerEntradasInv(JdbcTemplate intelisis, LocalDate desde) {
        if (movimientosEntradaInv.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT i.ID, i.Mov, i.MovID, i.FechaEmision, i.Proveedor, i.Referencia, i.Concepto "
                + "FROM Inv i WITH (NOLOCK) WHERE " + filtroEntradasInv();
        Map<Integer, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String mov = texto(rs, 2, 30);
            if (mov != null) {
                filas.putIfAbsent(id, new Object[]{
                        id, mov, texto(rs, 3, 20), fecha(rs, 4), texto(rs, 5, 20), texto(rs, 6, 100), texto(rs, 7, 100)});
            }
        }, parametrosEntradasInv(desde));
        return filas.values();
    }

    /** Lotes de esas entradas (en piezas, como la existencia). */
    private Collection<Object[]> leerLotesEntradasInv(JdbcTemplate intelisis, LocalDate desde) {
        if (movimientosEntradaInv.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT m.ID, m.Articulo, m.SerieLote, SUM(m.Cantidad) AS cantidad, MIN(m.FechaCaducidad) AS caducidad "
                + "FROM SerieLoteMov m WITH (NOLOCK) "
                + "JOIN Inv i WITH (NOLOCK) ON i.ID = m.ID "
                + "WHERE m.Modulo = 'INV' AND " + filtroEntradasInv() + " "
                + "GROUP BY m.ID, m.Articulo, m.SerieLote";
        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String articulo = texto(rs, 2, 20);
            String lote = texto(rs, 3, 50);
            BigDecimal cantidad = cero(rs.getBigDecimal(4));
            if (articulo == null || lote == null) {
                return;
            }
            String llave = id + "|" + articulo + "|" + lote;
            Object[] previa = filas.get(llave);
            if (previa != null) {
                previa[4] = ((BigDecimal) previa[4]).add(cantidad);
                return;
            }
            filas.put(llave, new Object[]{id, articulo, claveBase(articulo), lote, cantidad, fecha(rs, 5)});
        }, parametrosEntradasInv(desde));
        // Solo lo que de verdad entro (un renglon en cero o negativo no es una entrada).
        return filas.values().stream().filter(f -> ((BigDecimal) f[4]).signum() > 0).toList();
    }

    /**
     * Lo que Farmacia dio de baja por volumen en el almacen principal ("Consumo Farmacia" y
     * "Devolucion Farmacia"), por lote y en piezas, para verificarlo contra HAERO (HU32).
     * Por validar en la red del hospital: los nombres de los movimientos y el signo de
     * SerieLoteMov.Cantidad (se guarda en positivo; el tipo lo da el movimiento).
     */
    /**
     * HU38: consumo de Farmacia por clave y mes de los últimos 6 meses cerrados más el actual,
     * ya sumado en Intelisis (unos miles de renglones, no cada movimiento).
     */
    private Collection<Object[]> leerConsumoMensual(JdbcTemplate intelisis) {
        if (movimientosFarmacia.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT m.Articulo, YEAR(i.FechaEmision), MONTH(i.FechaEmision), "
                + "SUM(CASE WHEN i.Mov IN (" + marcadores(movimientosConsumo.size()) + ") THEN ABS(m.Cantidad) ELSE -ABS(m.Cantidad) END) "
                + "FROM SerieLoteMov m WITH (NOLOCK) "
                + "JOIN Inv i WITH (NOLOCK) ON i.ID = m.ID "
                + "WHERE m.Modulo = 'INV' AND i.Almacen = ? AND i.Estatus = 'CONCLUIDO' AND i.FechaEmision >= ? "
                + "AND i.Mov IN (" + marcadores(movimientosFarmacia.size()) + ") "
                + "GROUP BY m.Articulo, YEAR(i.FechaEmision), MONTH(i.FechaEmision)";
        List<Object> parametros = new ArrayList<>(movimientosConsumo);
        parametros.add(almacenPrincipal);
        parametros.add(Date.valueOf(java.time.YearMonth.now().minusMonths(6).atDay(1)));
        parametros.addAll(movimientosFarmacia);
        List<Object[]> renglones = new ArrayList<>();
        intelisis.query(sql, rs -> {
            String articulo = texto(rs, 1, 20);
            if (articulo != null) {
                renglones.add(new Object[]{articulo, rs.getInt(2), rs.getInt(3), cero(rs.getBigDecimal(4))});
            }
        }, parametros.toArray());
        return sumarConsumoMensual(renglones);
    }

    /** Junta la clave base (cajas) y la "-1" (piezas) del mismo mes en un renglón. */
    static Collection<Object[]> sumarConsumoMensual(List<Object[]> renglones) {
        Map<String, Object[]> filas = new LinkedHashMap<>();
        for (Object[] r : renglones) {
            String articulo = (String) r[0];
            String mes = String.format("%04d-%02d", (Integer) r[1], (Integer) r[2]);
            Object[] fila = filas.computeIfAbsent(claveBase(articulo) + "|" + mes,
                    k -> new Object[]{claveBase(articulo), mes, BigDecimal.ZERO, BigDecimal.ZERO});
            int columna = articulo.matches(".*-\\d+$") ? 3 : 2;
            fila[columna] = ((BigDecimal) fila[columna]).add((BigDecimal) r[3]);
        }
        return filas.values();
    }

    private Collection<Object[]> leerMovimientosFarmacia(JdbcTemplate intelisis, LocalDate desde) {
        if (movimientosFarmacia.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT i.ID, i.Mov, i.MovID, i.FechaEmision, m.Articulo, m.SerieLote, SUM(m.Cantidad) AS cantidad "
                + "FROM SerieLoteMov m WITH (NOLOCK) "
                + "JOIN Inv i WITH (NOLOCK) ON i.ID = m.ID "
                + "WHERE m.Modulo = 'INV' AND i.Almacen = ? AND i.Estatus = 'CONCLUIDO' AND i.FechaEmision >= ? "
                + "AND i.Mov IN (" + marcadores(movimientosFarmacia.size()) + ") "
                + "GROUP BY i.ID, i.Mov, i.MovID, i.FechaEmision, m.Articulo, m.SerieLote";
        List<Object> parametros = new ArrayList<>();
        parametros.add(almacenPrincipal);
        parametros.add(Date.valueOf(desde));
        parametros.addAll(movimientosFarmacia);
        Map<String, Object[]> filas = new LinkedHashMap<>();
        intelisis.query(sql, rs -> {
            int id = rs.getInt(1);
            String mov = texto(rs, 2, 30);
            Date fecha = fecha(rs, 4);
            String articulo = texto(rs, 5, 20);
            String lote = texto(rs, 6, 50);
            BigDecimal cantidad = cero(rs.getBigDecimal(7)).abs();
            if (mov == null || fecha == null || articulo == null || lote == null) {
                return;
            }
            String llave = id + "|" + articulo + "|" + lote;
            Object[] previa = filas.get(llave);
            if (previa != null) {
                previa[7] = ((BigDecimal) previa[7]).add(cantidad);
                return;
            }
            filas.put(llave, new Object[]{id, mov, texto(rs, 3, 20), fecha, articulo, claveBase(articulo), lote, cantidad});
        }, parametros.toArray());
        return filas.values().stream().filter(f -> ((BigDecimal) f[7]).signum() > 0).toList();
    }

    private void revisarQueNoVengaIncompleto(String que, int llegaron, String columnaBitacora) {
        // La primera vez no hay sincronización anterior con qué comparar.
        Integer anterior = haero.queryForList(
                "SELECT TOP 1 " + columnaBitacora + " FROM intelisis_sincronizacion WHERE estatus = 'OK' AND " + columnaBitacora
                        + " IS NOT NULL ORDER BY id DESC", Integer.class).stream().findFirst().orElse(null);
        if (anterior != null && anterior > 0 && llegaron < anterior / 2) {
            throw new IllegalStateException("Intelisis entregó " + llegaron + " " + que + " cuando la vez anterior fueron " + anterior
                    + ". Puede estar en mantenimiento; no se actualizó nada para no perder datos.");
        }
    }

    // ---------- Escritura en HAERO ----------

    /**
     * El catalogo se lee de Intelisis (diseno aprobado): pasa la copia de consulta a la
     * tabla medicamento, que es la que usan lotes, pedidos y solicitudes. Actualiza las
     * claves que ya existen y agrega las nuevas (con aviso informativo). En HAERO no se
     * captura nada del catalogo; lo unico propio de HAERO son los codigos de barras.
     */
    private void actualizarCatalogoHaero() {
        haero.execute("""
                IF OBJECT_ID('tempdb..#claves') IS NOT NULL DROP TABLE #claves;
                CREATE TABLE #claves (clave VARCHAR(30), accion NVARCHAR(10));
                DECLARE @primera BIT = CASE WHEN EXISTS (SELECT 1 FROM medicamento WHERE origen = 'INTELISIS') THEN 0 ELSE 1 END;

                MERGE medicamento AS t
                USING intelisis_medicamento AS s ON t.clave = s.clave
                WHEN MATCHED THEN UPDATE SET
                    nombre_generico = s.nombre_generico, presentacion = s.presentacion,
                    forma_farmaceutica = s.forma_farmaceutica, concentracion = s.concentracion,
                    grupo_terapeutico = s.grupo_terapeutico, cuadro_basico = s.cuadro_basico, estatus = s.estatus,
                    clave_cucop = s.clave_cucop, unidad_compra = s.unidad_compra, unidad_minima = s.unidad_minima,
                    riesgo = s.riesgo, principal_indicacion = s.principal_indicacion,
                    contraindicaciones = s.contraindicaciones, descripcion_completa = s.descripcion_completa,
                    origen = 'INTELISIS', sincronizado_en = SYSDATETIME()
                WHEN NOT MATCHED BY TARGET THEN INSERT
                    (clave, nombre_generico, presentacion, forma_farmaceutica, concentracion, grupo_terapeutico,
                     cuadro_basico, estatus, clave_cucop, unidad_compra, unidad_minima, riesgo, principal_indicacion,
                     contraindicaciones, descripcion_completa, origen, sincronizado_en)
                    VALUES (s.clave, s.nombre_generico, s.presentacion, s.forma_farmaceutica, s.concentracion,
                     s.grupo_terapeutico, s.cuadro_basico, s.estatus, s.clave_cucop, s.unidad_compra, s.unidad_minima,
                     s.riesgo, s.principal_indicacion, s.contraindicaciones, s.descripcion_completa, 'INTELISIS', SYSDATETIME())
                OUTPUT inserted.clave, $action INTO #claves (clave, accion);

                -- Clave nueva en Intelisis: se agrega sola y deja un aviso informativo.
                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, medicamento_clave)
                SELECT 'CLAVE_NUEVA', 'INFO', N'Clave nueva desde Intelisis',
                       CONCAT(c.clave, N' · ', LEFT(m.nombre_generico, 300), N'. Se agregó al catálogo.'), c.clave
                FROM #claves c
                JOIN medicamento m ON m.clave = c.clave
                WHERE c.accion = 'INSERT' AND @primera = 0;

                DROP TABLE #claves;
                """);
    }

    /** Piezas por caja del catalogo: las que Intelisis confirma con sus conversiones o su ficha. */
    private void actualizarPiezasPorCaja() {
        haero.update("""
                UPDATE m SET piezas_por_caja = CAST(f.piezas_por_caja AS INT)
                FROM medicamento m
                JOIN intelisis_factor f ON f.clave = m.clave
                WHERE f.origen IN ('CONVERSION', 'FICHA')
                  AND f.piezas_por_caja >= 1 AND f.piezas_por_caja = FLOOR(f.piezas_por_caja)
                  AND ISNULL(m.piezas_por_caja, -1) <> CAST(f.piezas_por_caja AS INT)
                """);
    }

    /**
     * Los pedidos se leen de Intelisis (diseno aprobado): el pedido, su estatus, su
     * contrato y lo esperado de cada partida son siempre los de Intelisis. HAERO solo
     * guarda lo que Almacen recibio fisicamente (pedido_detalle.cantidad_recibida), que
     * aqui nunca se toca. Avisos: pedido nuevo (informativo) y pedido o partida
     * cancelados en Intelisis (por revisar: "no recibir").
     */
    private void actualizarPedidosHaero() {
        haero.execute("""
                IF OBJECT_ID('tempdb..#ped') IS NOT NULL DROP TABLE #ped;
                IF OBJECT_ID('tempdb..#par') IS NOT NULL DROP TABLE #par;
                IF OBJECT_ID('tempdb..#nuevos') IS NOT NULL DROP TABLE #nuevos;

                SELECT p.compra_id,
                       -- Folio visible; si Intelisis repitiera el folio, se le agrega su id para no chocar.
                       'PP-' + p.mov_id + CASE WHEN COUNT(*) OVER (PARTITION BY p.mov_id) > 1
                                               THEN '-' + CAST(p.compra_id AS VARCHAR(12)) ELSE '' END AS numero_pedido,
                       LEFT(COALESCE(pr.nombre, p.proveedor, 'Sin proveedor'), 120) AS proveedor,
                       CASE WHEN p.estatus = 'CANCELADO' THEN 'CANCELADO' ELSE 'ACTIVO' END AS estatus,
                       p.estatus AS estatus_intelisis,
                       COALESCE(p.fecha_emision, CAST(GETDATE() AS date)) AS fecha,
                       CASE WHEN p.estatus = 'CANCELADO' THEN N'Cancelado en Intelisis' END AS motivo,
                       CASE WHEN p.estatus = 'CANCELADO' THEN COALESCE(p.fecha_cancelacion, CAST(p.fecha_emision AS datetime2)) END AS fecha_cancelacion,
                       COALESCE(p.contrato, CAST(p.contrato_id AS VARCHAR(20))) AS contrato, p.fecha_entrega
                INTO #ped
                FROM intelisis_pedido p
                LEFT JOIN intelisis_proveedor pr ON pr.proveedor = p.proveedor
                WHERE EXISTS (SELECT 1 FROM intelisis_pedido_partida pp JOIN medicamento m ON m.clave = pp.clave
                              WHERE pp.compra_id = p.compra_id);

                SELECT pp.compra_id, pp.clave, pp.cucop,
                       CAST(ROUND(CASE WHEN pp.cantidad - pp.cancelada < 0 THEN 0 ELSE pp.cantidad - pp.cancelada END, 0) AS INT) AS esperada,
                       CAST(ROUND(CASE WHEN pp.cantidad - pp.pendiente - pp.cancelada < 0 THEN 0
                                       ELSE pp.cantidad - pp.pendiente - pp.cancelada END, 0) AS INT) AS recibida,
                       CAST(ROUND(pp.cancelada, 0) AS INT) AS cancelada
                INTO #par
                FROM intelisis_pedido_partida pp
                JOIN medicamento m ON m.clave = pp.clave;

                DECLARE @primera BIT = CASE WHEN EXISTS (SELECT 1 FROM pedido WHERE compra_id_intelisis IS NOT NULL) THEN 0 ELSE 1 END;

                -- 1) Pedidos nuevos.
                CREATE TABLE #nuevos (id BIGINT PRIMARY KEY);
                INSERT INTO pedido (numero_pedido, proveedor, estatus, fecha, motivo_cancelacion, fecha_cancelacion, origen,
                                    compra_id_intelisis, contrato, fecha_entrega, estatus_intelisis)
                OUTPUT inserted.id INTO #nuevos (id)
                SELECT s.numero_pedido, s.proveedor, s.estatus, s.fecha, s.motivo, s.fecha_cancelacion, 'INTELISIS',
                       s.compra_id, s.contrato, s.fecha_entrega, s.estatus_intelisis
                FROM #ped s
                WHERE NOT EXISTS (SELECT 1 FROM pedido t WHERE t.compra_id_intelisis = s.compra_id);

                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, pedido_id)
                SELECT 'PEDIDO_NUEVO', 'INFO', N'Pedido nuevo en Intelisis',
                       CONCAT(N'Pedido ', pe.numero_pedido, N' de ', pe.proveedor,
                              CASE WHEN pe.contrato IS NOT NULL THEN CONCAT(N', contrato ', pe.contrato) ELSE N'' END,
                              CASE WHEN pe.fecha_entrega IS NOT NULL THEN CONCAT(N', entrega ', FORMAT(pe.fecha_entrega, 'dd/MM/yyyy')) ELSE N'' END, N'.'),
                       pe.id
                FROM #nuevos n
                JOIN pedido pe ON pe.id = n.id
                WHERE @primera = 0 AND pe.estatus_intelisis = 'PENDIENTE';

                IF @primera = 1 AND EXISTS (SELECT 1 FROM #nuevos)
                    INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle)
                    SELECT 'CARGA_INICIAL', 'INFO', N'Pedidos cargados desde Intelisis',
                           CONCAT(COUNT(*), N' pedidos del último año quedaron para consulta en HAERO.')
                    FROM #nuevos;

                -- 2) Pedido que se cancela en Intelisis: aviso por revisar, para no recibirlo.
                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, pedido_id)
                SELECT 'PEDIDO_CANCELADO', 'REVISAR', N'Pedido cancelado en Intelisis: no recibir',
                       CONCAT(N'Pedido ', t.numero_pedido, N' de ', t.proveedor, N' se canceló en Intelisis',
                              CASE WHEN r.recibidas > 0 THEN CONCAT(N'. Almacén ya había recibido ', r.recibidas, N' cajas en HAERO') ELSE N'' END,
                              N'. Ya no se puede recibir.'),
                       t.id
                FROM pedido t
                JOIN #ped s ON s.compra_id = t.compra_id_intelisis
                OUTER APPLY (SELECT SUM(d.cantidad_recibida) AS recibidas FROM pedido_detalle d WHERE d.pedido_id = t.id) r
                WHERE t.id NOT IN (SELECT id FROM #nuevos)
                  AND s.estatus_intelisis = 'CANCELADO' AND ISNULL(t.estatus_intelisis, '') <> 'CANCELADO';

                -- 3) El pedido siempre es el de Intelisis.
                UPDATE t SET estatus = s.estatus, proveedor = s.proveedor, motivo_cancelacion = s.motivo,
                             fecha_cancelacion = s.fecha_cancelacion, contrato = s.contrato, fecha_entrega = s.fecha_entrega,
                             estatus_intelisis = s.estatus_intelisis
                FROM pedido t
                JOIN #ped s ON s.compra_id = t.compra_id_intelisis
                WHERE t.estatus <> s.estatus OR t.proveedor <> s.proveedor OR ISNULL(t.contrato, '') <> ISNULL(s.contrato, '')
                   OR ISNULL(t.fecha_entrega, '19000101') <> ISNULL(s.fecha_entrega, '19000101')
                   OR ISNULL(t.estatus_intelisis, '') <> s.estatus_intelisis;

                -- 4) Partida que se cancela en Intelisis (sin cancelar todo el pedido): aviso por revisar.
                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, medicamento_clave, pedido_id)
                SELECT 'PARTIDA_CANCELADA', 'REVISAR', N'Partida cancelada en Intelisis: no recibir',
                       CONCAT(N'Pedido ', pe.numero_pedido, N', clave ', d.medicamento_clave, N': Intelisis canceló ',
                              p.cancelada - ISNULL(d.cancelada_intelisis, 0), N' cajas. Ahora se esperan ', p.esperada,
                              N'; HAERO lleva ', d.cantidad_recibida, N' recibidas.'),
                       d.medicamento_clave, pe.id
                FROM pedido_detalle d
                JOIN pedido pe ON pe.id = d.pedido_id
                JOIN #par p ON p.compra_id = pe.compra_id_intelisis AND p.clave = d.medicamento_clave
                WHERE pe.estatus <> 'CANCELADO' AND p.cancelada > ISNULL(d.cancelada_intelisis, 0);

                -- 5) Partidas: lo esperado y lo de Intelisis; lo recibido por HAERO no se toca.
                WITH destino AS (
                    SELECT d.* FROM pedido_detalle d
                    WHERE d.pedido_id IN (SELECT id FROM pedido WHERE compra_id_intelisis IS NOT NULL)
                )
                MERGE destino AS t
                USING (
                    SELECT pe.id AS pedido_id, p.clave, p.cucop, p.esperada, p.recibida, p.cancelada
                    FROM #par p
                    JOIN pedido pe ON pe.compra_id_intelisis = p.compra_id
                ) AS s
                ON t.pedido_id = s.pedido_id AND t.medicamento_clave = s.clave
                WHEN MATCHED AND (t.cantidad_esperada <> s.esperada OR ISNULL(t.cucop, '') <> ISNULL(s.cucop, '')
                                  OR ISNULL(t.recibida_intelisis, -1) <> s.recibida OR ISNULL(t.cancelada_intelisis, -1) <> s.cancelada) THEN
                    UPDATE SET cantidad_esperada = s.esperada, cucop = s.cucop,
                               recibida_intelisis = s.recibida, cancelada_intelisis = s.cancelada
                WHEN NOT MATCHED BY TARGET THEN
                    INSERT (pedido_id, medicamento_clave, cantidad_esperada, cantidad_recibida, cucop, recibida_intelisis, cancelada_intelisis)
                    VALUES (s.pedido_id, s.clave, s.esperada, 0, s.cucop, s.recibida, s.cancelada);

                DROP TABLE #ped;
                DROP TABLE #par;
                DROP TABLE #nuevos;
                """);
    }

    /**
     * Avisos de lotes (paso 4): compara la existencia por lote del almacen principal
     * contra lo que se vio la vez anterior (intelisis_lote_visto). Nunca cambia los
     * lotes de HAERO:
     *  - LOTE_CAMBIO (por revisar): Intelisis cambio la caducidad de un lote que
     *    Almacen tiene en HAERO con existencia.
     *  - LOTE_POR_DAR_DE_ALTA (informativo): lote nuevo en Intelisis de una clave
     *    dada de alta en HAERO (con codigo de barras) que HAERO todavia no tiene.
     * La primera vez solo se toma la foto, sin avisos.
     */
    private void actualizarAvisosLotes() {
        haero.update("""
                IF OBJECT_ID('tempdb..#ex') IS NOT NULL DROP TABLE #ex;
                DECLARE @almacen VARCHAR(10) = ?;
                DECLARE @primera BIT = CASE WHEN EXISTS (SELECT 1 FROM intelisis_lote_visto) THEN 0 ELSE 1 END;

                -- Existencia actual por clave y lote, en piezas (la clave base viene en cajas).
                SELECT x.clave_base AS clave, x.serie_lote, MIN(x.fecha_caducidad) AS caducidad,
                       SUM(CASE WHEN x.articulo = x.clave_base THEN x.existencia * ISNULL(m.piezas_por_caja, 1) ELSE x.existencia END) AS piezas
                INTO #ex
                FROM intelisis_existencia_lote x
                LEFT JOIN medicamento m ON m.clave = x.clave_base
                WHERE x.almacen = @almacen
                GROUP BY x.clave_base, x.serie_lote;

                -- 1) Caducidad cambiada en Intelisis sobre un lote que HAERO tiene con existencia.
                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, medicamento_clave, lote_id)
                SELECT 'LOTE_CAMBIO', 'REVISAR', N'Intelisis cambió la caducidad de un lote',
                       CONCAT(l.medicamento_clave, N' lote ', l.numero_lote, N': Intelisis cambió la caducidad de ',
                              ISNULL(FORMAT(v.caducidad, 'dd/MM/yyyy'), N'sin dato'), N' a ', ISNULL(FORMAT(e.caducidad, 'dd/MM/yyyy'), N'sin dato'),
                              N'. HAERO tiene ', FORMAT(l.caducidad, 'dd/MM/yyyy'), N' y no la cambió; revisa cuál es la correcta.'),
                       l.medicamento_clave, l.id
                FROM #ex e
                JOIN intelisis_lote_visto v ON v.clave = e.clave AND v.serie_lote = e.serie_lote
                JOIN lote l ON l.medicamento_clave = e.clave AND l.lote_intelisis = e.serie_lote
                WHERE ISNULL(v.caducidad, '19000101') <> ISNULL(e.caducidad, '19000101')
                  AND ISNULL(e.caducidad, '19000101') <> l.caducidad
                  AND EXISTS (SELECT 1 FROM existencia ex WHERE ex.lote_id = l.id AND ex.cantidad_cajas > 0);

                -- 2) Lote nuevo en Intelisis de una clave dada de alta, que HAERO todavía no tiene.
                INSERT INTO aviso_intelisis (tipo, nivel, titulo, detalle, medicamento_clave)
                SELECT 'LOTE_POR_DAR_DE_ALTA', 'INFO', N'Lote nuevo en Intelisis por recibir en HAERO',
                       CONCAT(e.clave, N' · ', LEFT(m.nombre_generico, 60), N', lote ', e.serie_lote,
                              CASE WHEN m.piezas_por_caja > 0
                                   THEN CONCAT(N': ', FORMAT(e.piezas / m.piezas_por_caja, '0.##'), N' cajas')
                                   ELSE CONCAT(N': ', FORMAT(e.piezas, '0.##'), N' piezas') END,
                              N'. Recíbelo en Registrar entrada cuando llegue la caja.'),
                       e.clave
                FROM #ex e
                JOIN medicamento m ON m.clave = e.clave
                WHERE @primera = 0
                  AND NOT EXISTS (SELECT 1 FROM intelisis_lote_visto v WHERE v.clave = e.clave AND v.serie_lote = e.serie_lote)
                  AND EXISTS (SELECT 1 FROM codigo_barras c WHERE c.medicamento_clave = e.clave)
                  AND NOT EXISTS (SELECT 1 FROM lote l WHERE l.medicamento_clave = e.clave
                                  AND (l.lote_intelisis = e.serie_lote OR l.numero_lote = e.serie_lote));

                -- 3) La foto de esta vez, para la siguiente comparación.
                MERGE intelisis_lote_visto AS t
                USING #ex AS s ON t.clave = s.clave AND t.serie_lote = s.serie_lote
                WHEN MATCHED THEN UPDATE SET caducidad = s.caducidad, piezas = s.piezas, visto_en = SYSDATETIME()
                WHEN NOT MATCHED BY TARGET THEN INSERT (clave, serie_lote, caducidad, piezas) VALUES (s.clave, s.serie_lote, s.caducidad, s.piezas)
                WHEN NOT MATCHED BY SOURCE THEN DELETE;

                DROP TABLE #ex;
                """, almacenPrincipal);
    }

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

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /** Numero entero positivo de un texto como "10" o "56"; null si no lo es. */
    private static BigDecimal numeroPositivo(String valor) {
        if (valor == null || !valor.strip().matches("\\d{1,6}")) {
            return null;
        }
        BigDecimal numero = new BigDecimal(valor.strip());
        return numero.signum() > 0 ? numero : null;
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
