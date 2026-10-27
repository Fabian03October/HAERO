package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RecibirDespachoRequest {
    private Long solicitudId;
    private List<PartidaRecibida> partidas;
    // Obligatorio si las cajas que llegaron no son las despachadas (HU26 CA2).
    private String motivo;
}
