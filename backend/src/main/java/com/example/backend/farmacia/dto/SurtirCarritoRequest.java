package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurtirCarritoRequest {
    private int idIngreso;
    private String clave;
    private Integer piezas;
    private String lote;
}
