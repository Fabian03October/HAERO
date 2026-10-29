package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

/** HU37: mover cajas de un lote de una ubicación a otra. */
@Getter
@Setter
public class ReubicacionRequest {
    private Long existenciaId;
    private Integer cajas;
    private String ubicacionDestino;
}
