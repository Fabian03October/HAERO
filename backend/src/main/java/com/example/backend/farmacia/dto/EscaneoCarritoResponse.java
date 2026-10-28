package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** A qué medicamento corresponde la caja y a qué pacientes del área les toca. */
@Getter
@AllArgsConstructor
public class EscaneoCarritoResponse {
    private String clave;
    private String medicamento;
    private String lote;
    private List<CandidatoCarrito> candidatos;
}
