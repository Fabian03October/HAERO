package com.example.backend.intelisis;

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
                "SELECT TOP 1 fin, articulos, proveedores, lotes_existencia, recepciones, lotes_recepcion FROM intelisis_sincronizacion "
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
                entero(ok.get("recepciones")), entero(ok.get("lotes_recepcion")));
    }

    /**
     * Compara, por clave, la existencia vigente de HAERO (cajas cargadas por
     * Excel) contra la del almacen principal en Intelisis (clave "-1"). En
     * ambos lados se ignoran los lotes ya caducados.
     */
    public List<ComparacionClaveResponse> comparacion() {
        String sql = """
                WITH h AS (
                    SELECT l.medicamento_clave AS clave, SUM(e.cantidad_cajas) AS cajas,
                           COUNT(DISTINCT l.id) AS lotes, MIN(l.caducidad) AS proxima
                    FROM existencia e
                    JOIN lote l ON l.id = e.lote_id
                    WHERE e.cantidad_cajas > 0 AND l.caducidad >= CAST(GETDATE() AS date)
                    GROUP BY l.medicamento_clave
                ), i AS (
                    SELECT x.clave_base AS clave, MIN(x.articulo) AS articulo, SUM(x.existencia) AS cantidad,
                           COUNT(*) AS lotes, MIN(x.fecha_caducidad) AS proxima
                    FROM intelisis_existencia_lote x
                    WHERE x.almacen = ? AND x.articulo = x.clave_base + '-1'
                      AND (x.fecha_caducidad IS NULL OR x.fecha_caducidad >= CAST(GETDATE() AS date))
                    GROUP BY x.clave_base
                )
                SELECT COALESCE(h.clave, i.clave) AS clave,
                       COALESCE(m.nombre_generico, a.descripcion) AS nombre,
                       h.cajas, h.lotes AS h_lotes, h.proxima AS h_proxima, m.piezas_por_caja,
                       i.articulo, i.cantidad, a.unidad, a.unidad_cantidad, i.lotes AS i_lotes, i.proxima AS i_proxima
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
            double unidadCantidad = rs.getDouble("unidad_cantidad");
            Double unidadCantidadONull = rs.wasNull() ? null : unidadCantidad;
            return new ComparacionClaveResponse(
                    rs.getString("clave"), rs.getString("nombre"), estado,
                    enteroONull(rs, "cajas"), enteroONull(rs, "h_lotes"), fecha(rs, "h_proxima"), enteroONull(rs, "piezas_por_caja"),
                    rs.getString("articulo"), rs.getBigDecimal("cantidad"), rs.getString("unidad"), unidadCantidadONull,
                    enteroONull(rs, "i_lotes"), fecha(rs, "i_proxima"));
        }, almacenPrincipal);
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
