package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

/** Lote del inventario de Farmacia (HU27). Las piezas son la unidad de Farmacia. */
@Getter
@AllArgsConstructor
public class LoteFarmaciaResponse {
    private Long id;
    private String clave;
    private String medicamento;
    private Integer piezasPorCaja;
    private String lote;
    private LocalDate caducidad;
    private Integer piezas;
    // Cajas completas (piezas / piezas por caja); null si el catálogo no trae piezas por caja.
    private Integer cajas;
    // FEFO (sale primero), ESPERA o VENCIDO.
    private String estado;
    // Caduca en los próximos 9 meses, igual que en Almacén.
    private boolean porCaducar;
    private Long loteAlmacenId;
}
