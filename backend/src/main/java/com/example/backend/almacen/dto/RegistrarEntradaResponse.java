package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class RegistrarEntradaResponse {
    private Long loteId;
    private String numeroLote;
    private LocalDate caducidad;
    private List<UbicacionCantidad> existencias;
}
