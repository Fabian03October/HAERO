package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class RegistrarEntradaResponse {
    private Long loteId;
    private String numeroLote;
    private LocalDate caducidad;
    private List<UbicacionCantidad> existencias;
    private String movimientoIntelisis;
    // Cajas según Intelisis (null si no se sabe) y cajas contadas por Almacén.
    private BigDecimal cajasIntelisis;
    private int cajasConfirmadas;
}
