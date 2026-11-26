package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** HU29: receta con lo indicado, lo entregado y lo pendiente de cada clave. */
@Getter
@AllArgsConstructor
public class RecetaResponse {
    private String folio;
    private String expediente;
    private String paciente;
    private String curp;
    private LocalDateTime fecha;
    private String despachador;
    private String estado;
    private List<ClaveRecetaResponse> claves;
    // Receta de Ventanilla a medias con más días que la vigencia: ya no se surte lo que faltó.
    private boolean vencida;
    // Veces que se resurtió con el mismo folio.
    private long resurtimientos;
    // Fecha de la última entrega (para ver cuánto ha pasado antes de resurtir).
    private LocalDateTime ultimaEntrega;
}
