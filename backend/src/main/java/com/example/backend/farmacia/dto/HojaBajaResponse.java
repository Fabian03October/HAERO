package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hoja de baja propia de HAERO (mismo acomodo que el "Consumo Farmacia" de Intelisis): una por
 * paciente y día, con folio consecutivo de HAERO. El folio se escribe en "Observaciones" al
 * capturar la baja en Intelisis.
 */
@Getter
@AllArgsConstructor
public class HojaBajaResponse {
    private int folio;
    private LocalDate dia;
    private LocalDateTime fecha;
    private String paciente;
    private String expediente;
    private String cama;
    private String area;
    // "Folio HAERO 000123 · 29/07/2026"
    private String observaciones;
    private List<PartidaBajaResponse> partidas;
    private int totalPiezas;
    private BigDecimal total;
    private String surtio;
    private boolean preciosDeIntelisis;
    // Para reimprimir la de un paciente; null en la baja por volumen (no es de un paciente).
    private Integer idIngreso;
}
