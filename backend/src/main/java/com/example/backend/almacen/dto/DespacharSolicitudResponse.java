package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DespacharSolicitudResponse {
    private Long solicitudId;
    private String estatus;
    private Integer cantidadSolicitada;
    private Integer cantidadAtendida;
}
