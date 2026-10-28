package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Carrito del día de un área de Hospitalización: lo que se surte a cada paciente. */
@Getter
@AllArgsConstructor
public class CarritoResponse {
    private String central;
    private LocalDate dia;
    // Hora de corte: lo indicado de 00:00 a esta hora es del día; después, "después del carrito".
    private String horaCorte;
    private int lineas;
    private int surtidas;
    // Cuándo y quién cerró el carrito de hoy; null si sigue abierto.
    private LocalDateTime cerradoEn;
    private String cerradoPor;
    private List<CamaCarrito> camas;
}
