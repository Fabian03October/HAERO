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
    // De dónde salió el dato: "HAERO", "Intelisis" o null si no hay registro.
    private String fuente;

    /** Paso con datos de HAERO: tiene fuente solo si hay fecha. */
    public PasoRecorridoResponse(String etapa, LocalDateTime fecha, String detalle) {
        this(etapa, fecha, detalle, fecha != null ? "HAERO" : null);
    }
}
