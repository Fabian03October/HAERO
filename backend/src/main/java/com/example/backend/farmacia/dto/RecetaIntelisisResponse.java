package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Receta firmada en Intelisis (vsa_Receta), para llenar la entrega sin teclear folio ni claves. */
@Getter
@AllArgsConstructor
public class RecetaIntelisisResponse {
    private String folio;
    // ID_Receta de Intelisis (el número que usa el envío a AAMATES).
    private String idReceta;
    private LocalDateTime fecha;
    // NOTA EVOLUCION, CONSULTA EXTERNA, TRIAGE...
    private String origen;
    private PacienteResponse paciente;
    // Estado de esa receta en HAERO (SURTIDA, PARCIAL o NO_DISPENSADA); null si aún no se registra.
    private String estadoEnHaero;
    private List<PartidaRecetaIntelisis> partidas;
}
