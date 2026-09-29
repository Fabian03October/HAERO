package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CargaInicialResponse {
    private boolean exito;
    private int totalRenglones;
    private int lotesCreados;
    private List<ErrorRenglonCargaInicial> errores;
}
