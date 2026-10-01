package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class MovimientoExternoRequest {
    // PRESTAMO o TRANSFERENCIA.
    private String tipo;
    // SALIDA (se presta o transfiere) o ENTRADA (se recibe de otra institucion).
    private String sentido;
    private String institucion;
    private Integer cajas;
    // Salida: existencia (lote y ubicacion) de donde sale.
    private Long existenciaId;
    // Entrada: datos del lote que se recibe.
    private String clave;
    private String numeroLote;
    private LocalDate caducidad;
    private String ubicacion;
}
