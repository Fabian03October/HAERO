package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Paciente leído de Intelisis en solo lectura. */
@Getter
@AllArgsConstructor
public class PacienteResponse {
    private String nombre;
    private String expediente;
    private String curp;
}
