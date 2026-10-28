package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Un medicamento de "Indicaciones médicas activas", con el texto como lo imprime Intelisis. */
@Getter
@AllArgsConstructor
public class MedicamentoHojaResponse {
    private String clave;
    // "PARACETAMOL SOLUCIÓN INYECTABLE …"
    private String descripcion;
    // "1 GRAMO Vía INTRAVENOSA Cada 8 horas; 3 Dosis para 24 horas;  Inicia: 28/jul/26 00:22"
    private String indicacion;
    private String nota;
    private boolean externo;
    private Integer folio;
}
