package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un lote de un medicamento, juntando lo que saben SICOMED e Intelisis, para elegir cuál rastrear. */
@Getter
@AllArgsConstructor
public class LoteTrazableResponse {
    private String clave;
    private String medicamento;
    // Número de lote sin el sufijo de Intelisis.
    private String lote;
    // Sufijos con los que aparece en Intelisis (origen del recurso: SCC, UNOPS, FONSABI...).
    private String sufijos;
    private LocalDate caducidad;
    // Piezas hoy en el inventario de Farmacia de SICOMED; null si el lote no está en Farmacia.
    private Integer piezasFarmacia;
    // Existencia en Intelisis: artículo sin "-1" (almacén) y con "-1" (farmacia), en sus unidades.
    private BigDecimal existenciaAlmacenIntelisis;
    private BigDecimal existenciaFarmaciaIntelisis;
    private boolean enHaero;
    private boolean enIntelisis;
}
