package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una partida devuelta (HU34). */
@Getter
@AllArgsConstructor
public class DevolucionResponse {
    private Long id;
    private String folioEntrega;
    private String clave;
    private String lote;
    private int piezas;
    private boolean danado;
    private LocalDateTime fecha;
    // RESTA (se resta en el día de la entrega) o DEVOLUCION ("Devolucion Farmacia" en el día de hoy).
    private String enResumen;
    private LocalDate diaResumen;
}
