package com.example.backend.intelisis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Aviso de un cambio en Intelisis. nivel INFO = solo para enterarse; REVISAR =
 * Intelisis cambió algo que afecta lo que Almacén ya movió en HAERO (HAERO no lo tocó).
 */
@Getter
@AllArgsConstructor
public class AvisoIntelisisResponse {
    private Long id;
    private LocalDateTime fecha;
    private String tipo;
    private String nivel;
    private String titulo;
    private String detalle;
    private String clave;
    private Long loteId;
    private Long pedidoId;
    private LocalDateTime revisadoEn;
    private String revisadoPor;
}
