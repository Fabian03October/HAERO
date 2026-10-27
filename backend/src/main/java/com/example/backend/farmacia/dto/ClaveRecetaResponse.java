package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ClaveRecetaResponse {
    private String clave;
    private String medicamento;
    private Integer piezasPorCaja;
    private int indicada;
    private int entregada;
    private int pendiente;
    private boolean noDispensada;
    private String motivo;
}
