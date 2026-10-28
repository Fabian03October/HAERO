package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Medicamento de las indicaciones del paciente (sin diagnósticos ni indicaciones generales). */
@Getter
@AllArgsConstructor
public class MedicamentoIndicado {
    private int id;
    private String clave;
    private String medicamento;
    // "PARACETAMOL 1 g VO c/8 h"
    private String descripcion;
    private Float dosis;
    private String unidad;
    private String via;
    private Integer cada;
    private String cadaUnidad;
    private LocalDateTime indicadoEn;
    private boolean activo;
    private LocalDateTime suspendidoEn;
}
