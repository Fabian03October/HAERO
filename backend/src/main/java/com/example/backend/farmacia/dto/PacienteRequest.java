package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PacienteRequest {
    private String nombre;
    private String expediente;
    private String curp;
}
