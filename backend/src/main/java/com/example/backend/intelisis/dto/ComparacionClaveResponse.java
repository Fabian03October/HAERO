package com.example.backend.intelisis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una clave comparada entre HAERO (lo cargado por Excel) e Intelisis (el espejo).
 * La cantidad de Intelisis va en su propia unidad (piezas, mg, ml, UI...), sin
 * convertir a cajas: la conversion espera la respuesta de Almacen/Farmacia.
 */
@Getter
@AllArgsConstructor
public class ComparacionClaveResponse {
    private String clave;
    private String nombre;
    // EN_AMBOS, SOLO_HAERO o SOLO_INTELISIS.
    private String estado;

    private Integer haeroCajas;
    private Integer haeroLotes;
    private LocalDate haeroProximaCaducidad;
    private Integer piezasPorCaja;

    private String intelisisArticulo;
    private BigDecimal intelisisCantidad;
    private String intelisisUnidad;
    private Double intelisisUnidadCantidad;
    private Integer intelisisLotes;
    private LocalDate intelisisProximaCaducidad;
}
