package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RenglonVerificacionResponse {
    private String claveIntelisis;
    private String lote;
    private int haero;
    private int intelisis;
    private int diferencia;
    // CUADRA, FALTA (falta dar de baja) o DE_MAS (se dio de más).
    private String resultado;
}
