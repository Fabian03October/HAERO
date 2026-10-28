package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Lo que se despliega al elegir una cama. */
@Getter
@AllArgsConstructor
public class DetalleCama {
    private CamaHospitalizacion cama;
    private List<DetallePaciente> pacientes;
    private List<EventoHospitalizacion> actualizaciones;
    private LocalDateTime sincronizadoEn;
}
