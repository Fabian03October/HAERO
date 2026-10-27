package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** HU34: entrega con lo que todavía se puede devolver de cada lote. */
@Getter
@AllArgsConstructor
public class EntregaEncontradaResponse {
    private Long id;
    private String folio;
    private String receta;
    private String expediente;
    private String paciente;
    private LocalDateTime fecha;
    private String despachador;
    private List<PartidaEntregadaResponse> partidas;
}
