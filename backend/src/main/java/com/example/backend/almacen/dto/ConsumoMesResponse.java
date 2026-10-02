package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConsumoMesResponse {
    private String mes;
    private int cajas;
}
