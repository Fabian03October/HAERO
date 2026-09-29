package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorRenglonCargaInicial {
    private Integer fila;
    private String mensaje;
}
