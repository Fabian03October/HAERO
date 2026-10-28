package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MedicoHojaResponse {
    private String nombre;
    private String cedula;
    private String especialidad;
}
