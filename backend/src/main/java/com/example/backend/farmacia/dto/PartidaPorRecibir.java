package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una salida de Almacén (un lote) por recibir. */
@Getter
@AllArgsConstructor
public class PartidaPorRecibir {
    private Long movimientoId;
    private String lote;
    private LocalDate caducidad;
    private Integer cajas;
    private LocalDateTime fechaDespacho;
    private String despachadoPor;
}
