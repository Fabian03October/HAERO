package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ExistenciaResponse {
    private Long existenciaId;
    private String clave;
    private String nombreGenerico;
    private Long loteId;
    private String numeroLote;
    private LocalDate caducidad;
    private String ubicacion;
    private Integer cajas;
    private boolean fefo;
}
