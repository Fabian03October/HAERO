package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un artículo de la hoja de baja, como en el "Consumo Farmacia" de Intelisis. */
@Getter
@AllArgsConstructor
public class PartidaBajaResponse {
    // Clave de Intelisis con "-1".
    private String articulo;
    private String descripcion;
    private int cantidad;
    private String lote;
    private LocalDate caducidad;
    // Leídos de Intelisis al imprimir; null si no hay conexión.
    private BigDecimal precio;
    private BigDecimal total;
    private String unidad;
    // Piezas de la clave distintas a la dosis del día: revisar después (solo en la hoja por paciente).
    private boolean revisar;
}
