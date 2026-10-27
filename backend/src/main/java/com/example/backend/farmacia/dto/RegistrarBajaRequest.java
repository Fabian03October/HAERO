package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarBajaRequest {
    private Long loteId;
    private Integer piezas;
    // CADUCIDAD, DANO o EXTRAVIO.
    private String motivo;
    private String detalle;
}
