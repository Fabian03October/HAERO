package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * "Indicaciones médicas activas" de un paciente, igual a la de Intelisis. Las indicaciones
 * generales, la dieta, los médicos, el sexo y la fecha de nacimiento se leen de Intelisis al
 * imprimir y no se guardan en HAERO.
 */
@Getter
@AllArgsConstructor
public class HojaIndicacionesResponse {
    private int idIngreso;
    private String paciente;
    private String sexo;
    // "27 Años, 2 Meses, 20 Días"
    private String edad;
    private LocalDate fechaNacimiento;
    private String expediente;
    private String cama;
    private String area;
    private LocalDateTime fechaImpresion;
    private List<String> indicacionesGenerales;
    private List<MedicamentoHojaResponse> medicamentosPorHorario;
    private List<MedicamentoHojaResponse> medicamentosPorInfusion;
    private String desayuno;
    private String comida;
    private String cena;
    private MedicoHojaResponse medicoTratante;
    private MedicoHojaResponse medicoAdscrito;
    // false: Intelisis no respondió y la hoja sale sin indicaciones generales, dieta ni médicos.
    private boolean intelisisDisponible;
}
