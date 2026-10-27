package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Recepción registrada; Almacén ve las que tienen diferencia (HU26 CA2). */
@Getter
@AllArgsConstructor
public class RecepcionResponse {
    private Long id;
    private Long solicitudId;
    private LocalDateTime fecha;
    private String usuario;
    private String clave;
    private String medicamento;
    private int cajasDespachadas;
    private int cajasRecibidas;
    private int piezasRecibidas;
    private int diferencia;
    private String motivo;
}
