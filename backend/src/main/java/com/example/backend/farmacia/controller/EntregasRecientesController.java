package com.example.backend.farmacia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que un paciente recibió en los últimos días, por clave: Nueva entrega lo usa para avisar
 * "este paciente ya recibió esta clave" y que nadie se lleve una dosis dos veces.
 */
@RestController
@RequestMapping("/api/farmacia/pacientes/{expediente}/entregas-recientes")
public class EntregasRecientesController {

    private static final int DIAS_POR_DEFECTO = 30;
    private static final int DIAS_MAXIMO = 90;

    private final JdbcTemplate haero;

    public EntregasRecientesController(JdbcTemplate haero) {
        this.haero = haero;
    }

    public record EntregaReciente(String clave, String medicamento, LocalDateTime fecha, int piezas, String folioEntrega,
                                  String folioReceta, String despachador) {
    }

    @GetMapping
    public ResponseEntity<List<EntregaReciente>> recientes(@PathVariable String expediente,
                                                           @RequestParam(defaultValue = "" + DIAS_POR_DEFECTO) int dias) {
        int periodo = Math.max(1, Math.min(DIAS_MAXIMO, dias));
        // La fecha sale de la aplicación: el SQL Server puede correr en otra zona horaria.
        Timestamp desde = Timestamp.valueOf(LocalDateTime.now().minusDays(periodo));
        List<EntregaReciente> lista = haero.query("""
                SELECT rc.medicamento_clave, m.nombre_generico, e.fecha, SUM(p.piezas - p.piezas_devueltas), e.id, r.folio, u.nombre_completo
                FROM farmacia_entrega_partida p
                JOIN farmacia_entrega e ON e.id = p.entrega_id
                JOIN farmacia_receta r ON r.id = e.receta_id
                JOIN farmacia_receta_clave rc ON rc.id = p.receta_clave_id
                JOIN medicamento m ON m.clave = rc.medicamento_clave
                JOIN usuario u ON u.id = e.usuario_id
                WHERE r.paciente_expediente = ? AND e.fecha >= ?
                GROUP BY rc.medicamento_clave, m.nombre_generico, e.fecha, e.id, r.folio, u.nombre_completo
                HAVING SUM(p.piezas - p.piezas_devueltas) > 0
                ORDER BY e.fecha DESC
                """, (rs, n) -> new EntregaReciente(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime(), rs.getInt(4),
                String.format("E-%06d", rs.getLong(5)), rs.getString(6), rs.getString(7)), expediente.trim(), desde);
        return ResponseEntity.ok(lista);
    }
}
