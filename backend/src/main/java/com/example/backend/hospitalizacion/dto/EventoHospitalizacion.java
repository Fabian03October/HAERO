package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Novedad detectada en Intelisis. */
@Getter
@AllArgsConstructor
public class EventoHospitalizacion {
    private long id;
    private LocalDateTime fecha;
    // INGRESO, EGRESO, CAMBIO_CAMA, MEDICAMENTO_NUEVO, MEDICAMENTO_SUSPENDIDO o MEDICAMENTO_MODIFICADO.
    private String tipo;
    private String espacio;
    private String central;
    private String paciente;
    private String detalle;
    private boolean visto;
    // Médico que indicó, cambió o suspendió el medicamento.
    private String medico;
}
