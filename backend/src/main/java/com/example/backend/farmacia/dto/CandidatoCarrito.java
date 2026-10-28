package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CandidatoCarrito {
    private int idIngreso;
    private String espacio;
    private String paciente;
    private int sugeridas;
    private int entregadas;
}
