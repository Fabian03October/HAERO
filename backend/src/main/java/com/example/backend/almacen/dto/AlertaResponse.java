package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AlertaResponse {
    private Long id;
    private String clave;
    private String nombreGenerico;
    private String tipo;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private int existencia;
    private double limite;
}
