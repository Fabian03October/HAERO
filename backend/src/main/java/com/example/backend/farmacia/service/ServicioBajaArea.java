package com.example.backend.farmacia.service;

import com.example.backend.farmacia.dto.HojaBajaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Baja por volumen de Quimioterapia y de Quirófano, igual que la de Hospitalización: en Intelisis se
 * captura UNA baja por área y día (totales por clave y lote) y el detalle por paciente queda en HAERO.
 * "Terminar el día" le pone hoja a cada paciente con algo entregado hoy y suma lo que todavía no
 * estaba en la baja del día (si se termina otra vez, se suma a la misma, con el mismo folio).
 * Se imprimen con las mismas hojas que Hospitalización (central = QUIMIO o QUIROFANO).
 */
@Service
public class ServicioBajaArea {

    // Área → prefijo del folio de las recetas que genera.
    static final Map<String, String> AREAS = Map.of(
            "QUIMIO", ServicioQuimioterapia.PREFIJO_FOLIO,
            "QUIROFANO", ServicioCajasQuirofano.PREFIJO_FOLIO);

    private final JdbcTemplate haero;
    private final ServicioHojasCarrito hojas;
    private final ServicioInventarioFarmacia inventario;

    public ServicioBajaArea(JdbcTemplate haero, ServicioHojasCarrito hojas, ServicioInventarioFarmacia inventario) {
        this.haero = haero;
        this.hojas = hojas;
        this.inventario = inventario;
    }

    public record EstadoBajaArea(String area, LocalDate dia, int pacientesPendientes, int entregasPendientes, Integer folioDelDia,
                                 LocalDateTime ultimaBaja, String ultimaBajaPor) {
    }

    /** Lo que falta de pasar a la baja del día y la baja que ya existe (si hay). */
    public EstadoBajaArea estado(String area) {
        String prefijo = prefijo(area);
        java.sql.Date hoy = java.sql.Date.valueOf(LocalDate.now());
        Map<String, Object> pendientes = haero.queryForMap("""
                SELECT COUNT(DISTINCT e.receta_id) AS pacientes, COUNT(*) AS entregas
                FROM farmacia_entrega e JOIN farmacia_receta r ON r.id = e.receta_id
                WHERE r.folio LIKE ? AND CAST(e.fecha AS date) <= ? AND e.baja_volumen_folio IS NULL
                """, prefijo + "%", hoy);
        List<Map<String, Object>> baja = haero.queryForList("""
                SELECT TOP 1 b.folio, b.fecha, u.nombre_completo FROM farmacia_baja_volumen b JOIN usuario u ON u.id = b.usuario_id
                WHERE b.dia = ? AND b.central = ? ORDER BY b.folio
                """, hoy, area);
        Map<String, Object> b = baja.isEmpty() ? Map.of() : baja.get(0);
        return new EstadoBajaArea(area, LocalDate.now(), ((Number) pendientes.get("pacientes")).intValue(),
                ((Number) pendientes.get("entregas")).intValue(), b.get("folio") == null ? null : ((Number) b.get("folio")).intValue(),
                b.get("fecha") instanceof Timestamp t ? t.toLocalDateTime() : null, (String) b.get("nombre_completo"));
    }

    @Transactional
    public List<HojaBajaResponse> terminar(String area, String nombreUsuario) {
        String prefijo = prefijo(area);
        long usuario = inventario.usuario(nombreUsuario).getId();
        LocalDate hoy = LocalDate.now();
        java.sql.Date dia = java.sql.Date.valueOf(hoy);
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());

        // Hoja por paciente (receta) con algo entregado que todavía no tiene. También lo de días
        // anteriores que no se pasó (por ejemplo, la caja del turno nocturno que regresó al otro día):
        // entra en la baja de HOY, que es cuando se captura en Intelisis; nada se queda sin baja.
        haero.update("""
                INSERT INTO farmacia_hoja_baja (receta_id, dia, central, espacio, fecha, usuario_id)
                SELECT DISTINCT r.id, ?, ?, NULL, ?, ?
                FROM farmacia_receta r JOIN farmacia_entrega e ON e.receta_id = r.id
                WHERE r.folio LIKE ? AND CAST(e.fecha AS date) <= ? AND e.baja_volumen_folio IS NULL
                  AND NOT EXISTS (SELECT 1 FROM farmacia_hoja_baja h WHERE h.receta_id = r.id)
                """, dia, area, ahora, usuario, prefijo + "%", dia);

        String sinBaja = "FROM farmacia_entrega e JOIN farmacia_hoja_baja h ON h.receta_id = e.receta_id "
                + "WHERE h.central = ? AND e.baja_volumen_folio IS NULL";
        Integer pendientes = haero.queryForObject("SELECT COUNT(*) " + sinBaja, Integer.class, area);
        if (pendientes == null || pendientes == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay entregas pendientes para dar de baja");
        }
        Integer existente = haero.queryForList("SELECT TOP 1 folio FROM farmacia_baja_volumen WHERE dia = ? AND central = ? ORDER BY folio",
                Integer.class, dia, area).stream().findFirst().orElse(null);
        int folio;
        if (existente != null) {
            folio = existente;
            haero.update("UPDATE farmacia_baja_volumen SET fecha = ?, usuario_id = ? WHERE folio = ?", ahora, usuario, folio);
        } else {
            folio = haero.queryForObject("INSERT INTO farmacia_baja_volumen (dia, central, fecha, usuario_id) OUTPUT INSERTED.folio VALUES (?, ?, ?, ?)",
                    Integer.class, dia, area, ahora, usuario);
        }
        haero.update("UPDATE e SET baja_volumen_folio = ? " + sinBaja, folio, area);
        return hojas.bajasVolumen(area, hoy, folio);
    }

    private static String prefijo(String area) {
        String prefijo = area == null ? null : AREAS.get(area);
        if (prefijo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El área debe ser QUIMIO o QUIROFANO");
        }
        return prefijo;
    }
}
