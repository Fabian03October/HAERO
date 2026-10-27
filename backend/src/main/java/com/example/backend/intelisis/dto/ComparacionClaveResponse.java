package com.example.backend.intelisis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una clave comparada en cajas entre HAERO (lo que dio de alta y recibió Almacén) e
 * Intelisis (la copia de consulta). Intelisis guarda piezas en la clave "-1" y cajas
 * en la clave base; se convierten con las piezas por caja del catálogo.
 */
@Getter
@AllArgsConstructor
public class ComparacionClaveResponse {
    private String clave;
    private String nombre;
    // EN_AMBOS, SOLO_HAERO o SOLO_INTELISIS.
    private String estado;

    // Almacén + Farmacia en cajas (las piezas de Farmacia entre las piezas por caja).
    private BigDecimal haeroCajas;
    private Integer haeroCajasAlmacen;
    private Integer farmaciaPiezas;
    private Integer haeroLotes;
    private LocalDate haeroProximaCaducidad;
    private Integer piezasPorCaja;

    private String intelisisArticulo;
    // Piezas en Intelisis y su equivalente en cajas (null si no se sabe cuántas piezas trae la caja).
    private BigDecimal intelisisPiezas;
    private BigDecimal intelisisCajas;
    private Integer intelisisLotes;
    private LocalDate intelisisProximaCaducidad;
}
