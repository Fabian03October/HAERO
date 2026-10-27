package com.example.backend.intelisis;

import com.example.backend.intelisis.dto.AvisoIntelisisResponse;
import com.example.backend.intelisis.dto.ComparacionClaveResponse;
import com.example.backend.intelisis.dto.EstadoIntelisisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de la integracion con Intelisis. Todo sale de las tablas espejo en
 * la base de HAERO (nunca de Intelisis), con una sola consulta por pantalla.
 */
@Service
public class ServicioIntelisis {

    private final ConexionIntelisis conexion;
    private final ServicioSincronizacionIntelisis sincronizacion;
    private final JdbcTemplate haero;
    private final String almacenPrincipal;

    public ServicioIntelisis(ConexionIntelisis conexion,
                             ServicioSincronizacionIntelisis sincronizacion,
                             JdbcTemplate haero,
                             @Value("${intelisis.almacenes:25301,25301-C}") String almacenes) {
        this.conexion = conexion;
        this.sincronizacion = sincronizacion;
        this.haero = haero;
        this.almacenPrincipal = almacenes.split(",")[0].trim();
    }

    public EstadoIntelisisResponse estado() {
        List<Map<String, Object>> ultima = haero.queryForList(
                "SELECT TOP 1 estatus, origen, inicio, fin, duracion_ms, error FROM intelisis_sincronizacion "
                        + "WHERE estatus <> 'EN_CURSO' ORDER BY id DESC");
        List<Map<String, Object>> ultimaOk = haero.queryForList(
                "SELECT TOP 1 fin, articulos, proveedores, lotes_existencia, recepciones, lotes_recepcion, medicamentos, pedidos, entradas_inv "
                        + "FROM intelisis_sincronizacion "
                        + "WHERE estatus = 'OK' ORDER BY id DESC");

        Map<String, Object> u = ultima.isEmpty() ? Map.of() : ultima.get(0);
        Map<String, Object> ok = ultimaOk.isEmpty() ? Map.of() : ultimaOk.get(0);

        return new EstadoIntelisisResponse(
                conexion.disponible(),
                conexion.disponible() ? null : conexion.motivoNoDisponible(),
                sincronizacion.estaEnCurso(),
                (String) u.get("estatus"), (String) u.get("origen"),
                fechaHora(u.get("inicio")), fechaHora(u.get("fin")),
                u.get("duracion_ms") != null ? ((Number) u.get("duracion_ms")).longValue() : null,
                (String) u.get("error"),
                fechaHora(ok.get("fin")),
                entero(ok.get("articulos")), entero(ok.get("proveedores")), entero(ok.get("lotes_existencia")),
                entero(ok.get("recepciones")), entero(ok.get("lotes_recepcion")),
                entero(ok.get("medicamentos")), entero(ok.get("pedidos")), entero(ok.get("entradas_inv")));
    }

    /**
     * Compara en cajas, por clave, la existencia vigente de HAERO contra la del almacen
     * principal en Intelisis (diseno aprobado: HAERO lleva el control y Supervision ve,
     * por ejemplo, 20 contra 50). Intelisis guarda piezas en la clave "-1" y cajas en la
     * clave base (antes de la Conversion); se pasa todo a cajas con las piezas por caja
     * del catalogo. En ambos lados se ignoran los lotes ya caducados.
     *
     * En Intelisis Almacen y Farmacia son el mismo almacen 25301, asi que del lado de HAERO
     * se suman las cajas de Almacen y las piezas de Farmacia pasadas a cajas: un despacho a
     * Farmacia no es una diferencia (HU27 CA6).
     */
    public List<ComparacionClaveResponse> comparacion() {
        String sql = """
                WITH ha AS (
                    SELECT l.medicamento_clave AS clave, SUM(e.cantidad_cajas) AS cajas,
                           COUNT(DISTINCT l.numero_lote) AS lotes, MIN(l.caducidad) AS proxima
                    FROM existencia e
                    JOIN lote l ON l.id = e.lote_id
                    WHERE e.cantidad_cajas > 0 AND l.caducidad >= CAST(GETDATE() AS date)
                    GROUP BY l.medicamento_clave
                ), hf AS (
                    SELECT f.medicamento_clave AS clave, SUM(f.piezas) AS piezas,
                           COUNT(*) AS lotes, MIN(f.caducidad) AS proxima
                    FROM farmacia_lote f
                    WHERE f.piezas > 0 AND f.caducidad >= CAST(GETDATE() AS date)
                    GROUP BY f.medicamento_clave
                ), h AS (
                    SELECT COALESCE(ha.clave, hf.clave) AS clave, ha.cajas AS cajas_almacen, hf.piezas AS piezas_farmacia,
                           CAST(COALESCE(ha.cajas, 0) + CASE WHEN mh.piezas_por_caja > 0 THEN COALESCE(hf.piezas, 0) * 1.0 / mh.piezas_por_caja ELSE 0 END
                                AS DECIMAL(19, 2)) AS cajas,
                           -- Un lote puede estar en los dos lados; se cuenta el mayor como aproximación.
                           CASE WHEN COALESCE(ha.lotes, 0) > COALESCE(hf.lotes, 0) THEN ha.lotes ELSE hf.lotes END AS lotes,
                           CASE WHEN hf.proxima IS NULL OR ha.proxima < hf.proxima THEN ha.proxima ELSE hf.proxima END AS proxima
                    FROM ha
                    FULL OUTER JOIN hf ON hf.clave = ha.clave
                    LEFT JOIN medicamento mh ON mh.clave = COALESCE(ha.clave, hf.clave)
                ), i AS (
                    SELECT x.clave_base AS clave, MAX(x.articulo) AS articulo,
                           SUM(CASE WHEN x.articulo = x.clave_base THEN x.existencia * m.piezas_por_caja ELSE x.existencia END) AS piezas,
                           -- Sin piezas por caja no se sabe cuántas cajas son: queda vacío en lugar de contar de menos.
                           CASE WHEN MAX(m.piezas_por_caja) IS NULL THEN NULL
                                ELSE SUM(CASE WHEN x.articulo = x.clave_base THEN x.existencia ELSE x.existencia / m.piezas_por_caja END) END AS cajas,
                           COUNT(DISTINCT x.serie_lote) AS lotes, MIN(x.fecha_caducidad) AS proxima
                    FROM intelisis_existencia_lote x
                    LEFT JOIN medicamento m ON m.clave = x.clave_base AND m.piezas_por_caja > 0
                    WHERE x.almacen = ? AND x.articulo IN (x.clave_base, x.clave_base + '-1')
                      AND (x.fecha_caducidad IS NULL OR x.fecha_caducidad >= CAST(GETDATE() AS date))
                    GROUP BY x.clave_base
                )
                SELECT COALESCE(h.clave, i.clave) AS clave,
                       COALESCE(m.nombre_generico, a.descripcion) AS nombre,
                       h.cajas, h.cajas_almacen, h.piezas_farmacia, h.lotes AS h_lotes, h.proxima AS h_proxima, m.piezas_por_caja,
                       i.articulo, i.piezas, CAST(ROUND(i.cajas, 2) AS DECIMAL(19, 2)) AS i_cajas,
                       i.lotes AS i_lotes, i.proxima AS i_proxima
                FROM h
                FULL OUTER JOIN i ON i.clave = h.clave
                LEFT JOIN medicamento m ON m.clave = COALESCE(h.clave, i.clave)
                LEFT JOIN intelisis_articulo a ON a.articulo = i.articulo
                ORDER BY COALESCE(h.clave, i.clave)
                """;

        return haero.query(sql, (rs, n) -> {
            boolean enHaero = rs.getObject("cajas") != null;
            boolean enIntelisis = rs.getObject("articulo") != null;
            String estado = enHaero && enIntelisis ? "EN_AMBOS" : enHaero ? "SOLO_HAERO" : "SOLO_INTELISIS";
            return new ComparacionClaveResponse(
                    rs.getString("clave"), rs.getString("nombre"), estado,
                    rs.getBigDecimal("cajas"), enteroONull(rs, "cajas_almacen"), enteroONull(rs, "piezas_farmacia"),
                    enteroONull(rs, "h_lotes"), fecha(rs, "h_proxima"), enteroONull(rs, "piezas_por_caja"),
                    rs.getString("articulo"), rs.getBigDecimal("piezas"), rs.getBigDecimal("i_cajas"),
                    enteroONull(rs, "i_lotes"), fecha(rs, "i_proxima"));
        }, almacenPrincipal);
    }

    // ---------- Avisos de cambios en Intelisis ----------

    /** Los 500 más recientes, pendientes (sin revisar) o revisados; los "por revisar" primero. */
    public List<AvisoIntelisisResponse> avisos(boolean pendientes) {
        String sql = """
                SELECT TOP (500) a.id, a.fecha, a.tipo, a.nivel, a.titulo, a.detalle, a.medicamento_clave, a.lote_id, a.pedido_id,
                       a.revisado_en, u.nombre_completo AS revisado_por
                FROM aviso_intelisis a
                LEFT JOIN usuario u ON u.id = a.revisado_por
                WHERE {FILTRO}
                ORDER BY CASE WHEN a.nivel = 'REVISAR' THEN 0 ELSE 1 END, a.fecha DESC, a.id DESC
                """.replace("{FILTRO}", pendientes ? "a.revisado_en IS NULL" : "a.revisado_en IS NOT NULL");
        return haero.query(sql, (rs, n) -> new AvisoIntelisisResponse(
                rs.getLong("id"), fechaHora(rs.getTimestamp("fecha")), rs.getString("tipo"), rs.getString("nivel"),
                rs.getString("titulo"), rs.getString("detalle"), rs.getString("medicamento_clave"),
                largoONull(rs, "lote_id"), largoONull(rs, "pedido_id"), fechaHora(rs.getTimestamp("revisado_en")),
                rs.getString("revisado_por")));
    }

    /** Cuántos avisos sin revisar hay de cada nivel (para el aviso del Inicio y los contadores). */
    public Map<String, Integer> resumenAvisos() {
        Map<String, Integer> resumen = new LinkedHashMap<>();
        resumen.put("REVISAR", 0);
        resumen.put("INFO", 0);
        haero.query("SELECT nivel, COUNT(*) AS total FROM aviso_intelisis WHERE revisado_en IS NULL GROUP BY nivel",
                rs -> {
                    resumen.put(rs.getString("nivel"), rs.getInt("total"));
                });
        return resumen;
    }

    /** Marca un aviso como revisado por el usuario. Regresa false si no existe o ya estaba revisado. */
    public boolean marcarRevisado(long id, String nombreUsuario) {
        return haero.update("""
                UPDATE aviso_intelisis
                SET revisado_en = SYSDATETIME(), revisado_por = (SELECT id FROM usuario WHERE nombre_usuario = ?)
                WHERE id = ? AND revisado_en IS NULL
                """, nombreUsuario, id) > 0;
    }

    /** Marca como revisados todos los avisos informativos pendientes; los "por revisar" se quedan. */
    public int marcarInformativosRevisados(String nombreUsuario) {
        return haero.update("""
                UPDATE aviso_intelisis
                SET revisado_en = SYSDATETIME(), revisado_por = (SELECT id FROM usuario WHERE nombre_usuario = ?)
                WHERE revisado_en IS NULL AND nivel = 'INFO'
                """, nombreUsuario);
    }

    private static Long largoONull(ResultSet rs, String columna) throws SQLException {
        long valor = rs.getLong(columna);
        return rs.wasNull() ? null : valor;
    }

    private static Integer enteroONull(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }

    private static LocalDateTime fechaHora(Object valor) {
        if (valor instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (valor instanceof LocalDateTime l) {
            return l;
        }
        return null;
    }

    private static Integer entero(Object valor) {
        return valor instanceof Number n ? n.intValue() : null;
    }
}
