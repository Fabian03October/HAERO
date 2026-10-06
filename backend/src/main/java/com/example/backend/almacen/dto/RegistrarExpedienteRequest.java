package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Alta de un préstamo o transferencia. Llega como parte "datos" de una petición
 * multipart, junto con el PDF de solicitud (préstamo) o de envío (transferencia).
 */
@Getter
@Setter
public class RegistrarExpedienteRequest extends MovimientoExternoRequest {
    // Obligatoria en préstamos: fecha comprometida de devolución.
    private LocalDate fechaLimite;
    private String observaciones;
}
