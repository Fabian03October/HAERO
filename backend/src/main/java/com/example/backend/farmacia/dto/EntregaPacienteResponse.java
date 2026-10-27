package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class EntregaPacienteResponse {
    private LocalDateTime fecha;
    private String folio;
    private String paciente;
    private String expediente;
    private String clave;
    private String lote;
    private int piezas;
    private String despachador;
}
