package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MedicamentoResponse {
    private String clave;
    private String nombreGenerico;
    private String presentacion;
    private Integer piezasPorCaja;
    private String descripcion;
}
