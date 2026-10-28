package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PacienteInternado {
    private int idIngreso;
    private String nombre;
    private String expediente;
    private String servicio;
    private LocalDateTime fechaIngreso;
    private Integer diasInternado;
    private int medicamentosActivos;
}
