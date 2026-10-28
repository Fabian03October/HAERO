package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SurtidoCarritoResponse {
    private String folioEntrega;
    private List<PartidaEntregadaResponse> partidas;
    private LineaCarrito linea;
}
