package com.example.backend.almacen.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Reportes de existencias que pidió Supervisión: por lote, cuántas cajas llegaron, cuántas
 * salieron a Farmacia y cuántas quedan (en Almacén y, en piezas, en Farmacia). Los caducados se
 * incluyen con su estatus: siguen siendo bienes del hospital hasta su baja definitiva.
 */
@RestController
@RequestMapping("/api/almacen/reportes")
public class ReportesAlmacenController {

    private final JdbcTemplate haero;

    public ReportesAlmacenController(JdbcTemplate haero) {
        this.haero = haero;
    }

    public record ReporteLote(Long loteId, String clave, String medicamento, String numeroLote, LocalDate caducidad,
                              String estatus, String proveedor, int cajasLlegaron, int cajasAFarmacia, int cajasOtrasSalidas,
                              int cajasQuedan, int piezasEnFarmacia) {
    }

    @GetMapping("/lotes")
    public ResponseEntity<List<ReporteLote>> lotes() {
        // Entradas: recepción (ENTRADA), alta inicial (CARGA_INICIAL) y lo que llegó por canje,
        // préstamo o transferencia (sentido ENTRADA). Las reubicaciones no cuentan: solo mueven cajas.
        String sql = """
                SELECT l.id, l.medicamento_clave, m.nombre_generico, l.numero_lote, l.caducidad, l.estatus, l.proveedor,
                       COALESCE(mv.llegaron, 0), COALESCE(mv.a_farmacia, 0), COALESCE(mv.otras_salidas, 0),
                       COALESCE(ex.quedan, 0), COALESCE(fl.piezas, 0)
                FROM lote l
                JOIN medicamento m ON m.clave = l.medicamento_clave
                LEFT JOIN (
                    SELECT e.lote_id,
                           SUM(CASE WHEN x.tipo IN ('ENTRADA', 'CARGA_INICIAL')
                                     OR (x.tipo NOT IN ('REUBICACION', 'AJUSTE') AND x.sentido = 'ENTRADA')
                                    THEN x.cantidad_cajas ELSE 0 END) AS llegaron,
                           SUM(CASE WHEN x.tipo = 'SALIDA' THEN x.cantidad_cajas ELSE 0 END) AS a_farmacia,
                           SUM(CASE WHEN x.tipo NOT IN ('SALIDA', 'REUBICACION', 'AJUSTE', 'ENTRADA', 'CARGA_INICIAL')
                                     AND x.sentido = 'SALIDA' THEN x.cantidad_cajas ELSE 0 END) AS otras_salidas
                    FROM movimiento x JOIN existencia e ON e.id = x.existencia_id
                    GROUP BY e.lote_id
                ) mv ON mv.lote_id = l.id
                LEFT JOIN (SELECT lote_id, SUM(cantidad_cajas) AS quedan FROM existencia GROUP BY lote_id) ex ON ex.lote_id = l.id
                LEFT JOIN (SELECT lote_id, SUM(piezas) AS piezas FROM farmacia_lote WHERE lote_id IS NOT NULL GROUP BY lote_id) fl
                       ON fl.lote_id = l.id
                ORDER BY l.medicamento_clave, l.caducidad, l.numero_lote
                """;
        return ResponseEntity.ok(haero.query(sql, (rs, n) -> new ReporteLote(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getDate(5).toLocalDate(), rs.getString(6), rs.getString(7), rs.getInt(8), rs.getInt(9),
                rs.getInt(10), rs.getInt(11), rs.getInt(12))));
    }
}
