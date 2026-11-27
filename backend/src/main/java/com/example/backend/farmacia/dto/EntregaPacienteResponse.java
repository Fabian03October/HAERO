package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class EntregaPacienteResponse {
    private LocalDateTime fecha;
    private String folio;
    private String paciente;
    private String expediente;
    private String clave;
    private String lote;
    private int piezas;
    private String despachador;
    // "HAERO" (entrega registrada en SICOMED) o "Intelisis" (baja de Farmacia anterior a SICOMED).
    private String fuente;
    // Médico de la receta (solo entregas de SICOMED; las bajas de Intelisis no lo traen).
    private String medico;
    // Fuente de financiamiento (script de almacén): "SAEAC · Donacion FONSABI 953 · orden …"; solo consumos de Intelisis.
    @lombok.Setter
    private String financiamiento;

    public EntregaPacienteResponse(LocalDateTime fecha, String folio, String paciente, String expediente, String clave, String lote,
                                   int piezas, String despachador, String fuente) {
        this(fecha, folio, paciente, expediente, clave, lote, piezas, despachador, fuente, null, null);
    }

    public EntregaPacienteResponse(LocalDateTime fecha, String folio, String paciente, String expediente, String clave, String lote,
                                   int piezas, String despachador, String fuente, String medico) {
        this(fecha, folio, paciente, expediente, clave, lote, piezas, despachador, fuente, medico, null);
    }
}
