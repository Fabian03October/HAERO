package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PasoRecorridoResponse {
    private String etapa;
    private LocalDateTime fecha;
    private String detalle;
}
