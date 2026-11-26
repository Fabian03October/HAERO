package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** HU28: entrega de una receta, o una entrega más que completa una receta parcial (HU29). */
@Getter
@Setter
public class RegistrarEntregaRequest {
    private String folioReceta;
    private PacienteRequest paciente;
    private List<ClaveRecetaRequest> claves;
    private List<CajaEntregaRequest> cajas;
    // Si al paciente ya se le entregó alguna de estas claves en los últimos días: por qué se entrega otra vez.
    private String justificacionRepetida;
}
