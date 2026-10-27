package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class PartidaEntregadaResponse {
    private Long partidaId;
    private String clave;
    private String medicamento;
    private String lote;
    private LocalDate caducidad;
    private int entregada;
    private int devuelta;
}
