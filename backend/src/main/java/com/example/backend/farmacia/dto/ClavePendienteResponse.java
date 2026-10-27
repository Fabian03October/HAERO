package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Claves más pendientes de las recetas parciales (base de las alertas de desabasto). */
@Getter
@AllArgsConstructor
public class ClavePendienteResponse {
    private String clave;
    private String medicamento;
    private int piezas;
    private int recetas;
}
