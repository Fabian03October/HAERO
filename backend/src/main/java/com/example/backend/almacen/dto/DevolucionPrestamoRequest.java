package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Devolución (total o parcial) de un préstamo. Va en sentido contrario al préstamo:
 * - Si el hospital prestó (SALIDA), la devolución entra: lote, caducidad y ubicación de lo que se recibe.
 * - Si al hospital le prestaron (ENTRADA), la devolución sale: existencia (lote y ubicación) de donde sale.
 */
@Getter
@Setter
public class DevolucionPrestamoRequest {
    private Integer cajas;
    private Long existenciaId;
    private String numeroLote;
    private LocalDate caducidad;
    private String ubicacion;
}
