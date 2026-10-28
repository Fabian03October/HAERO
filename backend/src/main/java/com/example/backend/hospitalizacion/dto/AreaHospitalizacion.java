package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Un área (central de enfermería) con el resumen de sus camas. */
@Getter
@AllArgsConstructor
public class AreaHospitalizacion {
    private String central;
    private String nombre;
    private int camas;
    private int ocupadas;
    private int libres;
    private int pacientes;
    // Camas con novedades que este usuario no ha visto.
    private int conNovedades;
}
