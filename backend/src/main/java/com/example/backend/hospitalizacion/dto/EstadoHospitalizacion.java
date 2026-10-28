package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class EstadoHospitalizacion {
    private boolean intelisisDisponible;
    private boolean enCurso;
    private LocalDateTime ultimaLectura;
    private String estatus;
    private String error;
    private int camas;
    private int internados;
}
