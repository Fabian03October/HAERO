package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClaveRecetaRequest {
    private String clave;
    private Integer piezasIndicadas;
    private boolean noDispensada;
    private String motivo;
}
