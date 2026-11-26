package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Historial de la cama (últimos 7 días): lo que tocaba surtir cada día y cómo quedó. */
@Getter
@AllArgsConstructor
public class HistorialCamaResponse {
    private int idIngreso;
    private String espacio;
    private String paciente;
    private String expediente;
    // Del más reciente (hoy) al más antiguo; solo los días con algo indicado.
    private List<Dia> dias;

    /**
     * Un día: sus renglones como en el carrito de ese día. surtidasDespues = claves que quedaron
     * pendientes ese día pero se surtieron un día posterior (ya no se ofrecen para surtir).
     */
    public record Dia(LocalDate dia, List<LineaCarrito> lineas, Set<String> surtidasDespues) {
    }
}
