package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class EntregaResponse {
    private Long id;
    private String folio;
    private LocalDateTime fecha;
    private List<PartidaEntregadaResponse> partidas;
    private RecetaResponse receta;
}
