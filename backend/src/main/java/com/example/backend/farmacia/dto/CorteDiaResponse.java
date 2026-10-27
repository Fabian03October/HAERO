package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HU31: resumen del día para dar una sola baja por volumen en Intelisis. */
@Getter
@AllArgsConstructor
public class CorteDiaResponse {
    private LocalDate dia;
    // PENDIENTE, DADO_DE_BAJA o VERIFICADO.
    private String estado;
    private String folioIntelisis;
    private LocalDateTime marcadoEn;
    private String marcadoPor;
    // Entregas, devoluciones o bajas registradas después de marcar el día (hay que volver a darlo de baja).
    private int cambiosDespuesDeLaBaja;
    private List<RenglonResumenResponse> consumo;
    private List<RenglonResumenResponse> devoluciones;
    private List<RenglonBajaResponse> bajas;
    private int totalClaves;
    private int totalPiezas;
    private int totalPacientes;
    private List<LocalDate> diasPendientes;
}
