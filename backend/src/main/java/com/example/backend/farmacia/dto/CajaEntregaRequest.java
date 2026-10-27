package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

/** Caja escaneada. Sin lote (el código no es GS1) se toma el lote FEFO (HU30). */
@Getter
@Setter
public class CajaEntregaRequest {
    private String clave;
    private String lote;
    private Integer piezas;
}
