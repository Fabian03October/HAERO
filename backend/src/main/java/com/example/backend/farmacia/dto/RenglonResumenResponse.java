package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Por clave de Intelisis (con "-1") y lote, en piezas. */
@Getter
@AllArgsConstructor
public class RenglonResumenResponse {
    private String claveIntelisis;
    private String clave;
    private String medicamento;
    private String lote;
    private int piezas;
    private Integer piezasPorCaja;
    private int pacientes;
}
