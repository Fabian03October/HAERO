package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurtirCarritoRequest {
    private int idIngreso;
    private String clave;
    private Integer piezas;
    private String lote;
    // Desde el historial de la cama: se acepta lo que quedó sin surtir en los últimos 7 días.
    private boolean desdeHistorial;
}
