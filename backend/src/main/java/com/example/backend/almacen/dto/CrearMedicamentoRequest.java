package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearMedicamentoRequest {
    private String clave;
    private String nombreGenerico;
    private String presentacion;
    private Integer piezasPorCaja;
    private String descripcion;
}
