package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.almacen.repository.MovimientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta de movimientos (HU14 CA5 "salidas registradas" y HU16 CPM): cada
 * entrada, carga inicial y despacho deja un Movimiento; aqui solo se leen.
 */
@Service
public class ServicioMovimientos {

    private final MovimientoRepository movimientoRepository;

    public ServicioMovimientos(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    public List<MovimientoResponse> listar(String tipo, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser anterior a la inicial");
        }
        LocalDateTime inicio = desde != null ? desde.atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime fin = hasta != null ? hasta.plusDays(1).atStartOfDay().minusNanos(1) : LocalDateTime.of(9999, 12, 31, 23, 59);
        return movimientoRepository.findByFechaBetweenOrderByFechaDesc(inicio, fin).stream()
                .filter(m -> tipo == null || tipo.isBlank() || m.getTipo().equalsIgnoreCase(tipo))
                .map(this::aRespuesta)
                .toList();
    }

    MovimientoResponse aRespuesta(Movimiento m) {
        Lote lote = m.getExistencia().getLote();
        Solicitud solicitud = m.getSolicitud();
        return new MovimientoResponse(
                m.getId(),
                m.getTipo(),
                m.getFecha(),
                m.getUsuario().getNombreCompleto(),
                solicitud != null ? solicitud.getId() : null,
                lote.getMedicamento().getClave(),
                lote.getMedicamento().getNombreGenerico(),
                lote.getNumeroLote(),
                lote.getCaducidad(),
                m.getExistencia().getUbicacion(),
                m.getCantidadCajas(),
                m.getSentido(),
                m.getInstitucionExterna(),
                solicitud != null ? solicitud.getUsuario().getNombreCompleto() : null,
                solicitud != null ? solicitud.getFecha() : null,
                solicitud != null ? solicitud.getCantidadSolicitada() : null,
                esDevolucion(m)
        );
    }

    /** Un movimiento de un expediente que va en sentido contrario al préstamo es su devolución. */
    static boolean esDevolucion(Movimiento m) {
        return m.getExpediente() != null && m.getSentido() != null
                && !m.getSentido().equalsIgnoreCase(m.getExpediente().getSentido());
    }
}
