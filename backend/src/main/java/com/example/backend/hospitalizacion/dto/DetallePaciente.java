package com.example.backend.hospitalizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DetallePaciente {
    private PacienteInternado paciente;
    private List<MedicamentoIndicado> medicamentos;
}
