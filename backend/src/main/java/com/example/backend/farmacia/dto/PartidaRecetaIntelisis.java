package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Clave indicada en la receta de Intelisis (vsa_RecetaD). */
@Getter
@AllArgsConstructor
public class PartidaRecetaIntelisis {
    // Clave del catálogo de HAERO; null si el médico escribió el fármaco sin clave.
    private String clave;
    private String medicamento;
    private Integer piezasPorCaja;
    private int cantidad;
    private boolean enCatalogo;
}
