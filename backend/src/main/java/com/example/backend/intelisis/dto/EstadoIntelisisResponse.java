package com.example.backend.intelisis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Estado de la integracion y de la ultima sincronizacion. */
@Getter
@AllArgsConstructor
public class EstadoIntelisisResponse {
    private boolean disponible;
    // Por que no esta disponible (null si lo esta).
    private String motivo;
    private boolean enCurso;

    // Ultima corrida terminada (OK o ERROR); null si nunca ha corrido.
    private String ultimoEstatus;
    private String ultimoOrigen;
    private LocalDateTime ultimoInicio;
    private LocalDateTime ultimoFin;
    private Long ultimaDuracionMs;
    private String ultimoError;

    // Ultima corrida exitosa: de cuando son los datos del espejo.
    private LocalDateTime datosAl;
    private Integer articulos;
    private Integer proveedores;
    private Integer lotesExistencia;
    private Integer recepciones;
    private Integer lotesRecepcion;
}
