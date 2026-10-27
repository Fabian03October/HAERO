package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RenglonBajaResponse {
    private String claveIntelisis;
    private String lote;
    private int piezas;
    private String motivo;
}
