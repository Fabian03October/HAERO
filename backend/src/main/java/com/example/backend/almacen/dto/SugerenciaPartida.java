package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class SugerenciaPartida {
    private Long existenciaId;
    private Long loteId;
    private String numeroLote;
    private LocalDate caducidad;
    private String ubicacion;
    private Integer cajasDisponibles;
    private Integer cajasSugeridas;
}
