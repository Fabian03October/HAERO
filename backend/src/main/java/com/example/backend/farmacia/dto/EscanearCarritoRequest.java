package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

/** Caja escaneada en el carrito; el lote viene del código GS1 si lo trae. */
@Getter
@Setter
public class EscanearCarritoRequest {
    private String central;
    private String codigo;
    private String lote;
}
