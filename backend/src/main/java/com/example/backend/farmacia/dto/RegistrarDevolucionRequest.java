package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RegistrarDevolucionRequest {
    private Long entregaId;
    private String motivo;
    private List<PartidaDevueltaRequest> partidas;
}
