package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BajaResponse {
    private Long id;
    private String clave;
    private String medicamento;
    private String lote;
    private LocalDate caducidad;
    private int piezas;
    private String motivo;
    private String detalle;
    private LocalDateTime fecha;
    private String usuario;
    private boolean deDevolucion;
}
