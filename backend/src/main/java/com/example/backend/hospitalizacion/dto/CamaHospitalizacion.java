package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** Una cama con quién está y cuántas novedades sin ver tiene. */
@Getter
@AllArgsConstructor
public class CamaHospitalizacion {
    private String espacio;
    private String nombre;
    private String tipo;
    private String central;
    // LIBRE u OCUPADA.
    private String estado;
    // Normalmente uno; puede haber más (sillones de urgencias o registros sin cerrar en Intelisis).
    private List<PacienteInternado> pacientes;
    private int novedades;
}
