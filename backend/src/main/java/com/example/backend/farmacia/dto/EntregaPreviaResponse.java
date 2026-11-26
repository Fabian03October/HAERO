package com.example.backend.farmacia.dto;

import java.time.LocalDateTime;

/** Lo que ya se le entregó al paciente de una clave en los últimos días (para avisar antes de repetir). */
public record EntregaPreviaResponse(LocalDateTime fecha, String folioEntrega, String receta, String area, String clave,
                                    String medicamento, int piezas) {
}
