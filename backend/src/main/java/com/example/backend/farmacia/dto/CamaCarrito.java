package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CamaCarrito {
    private String espacio;
    private int idIngreso;
    private String paciente;
    private String expediente;
    private List<LineaCarrito> lineas;
}
