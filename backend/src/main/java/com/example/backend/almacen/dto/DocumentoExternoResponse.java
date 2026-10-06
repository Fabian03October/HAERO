package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Datos de un PDF del expediente, sin el archivo (se descarga aparte). */
@Getter
@AllArgsConstructor
public class DocumentoExternoResponse {
    private Long id;
    private Long expedienteId;
    private String tipo;
    private String nombreArchivo;
    private Integer tamano;
    private LocalDateTime fecha;
    private String usuario;
}
