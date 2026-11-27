package com.example.backend.intelisis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Un lote de una clave, en piezas, entre SICOMED (Almacén en cajas × piezas por caja + Farmacia en piezas) e
 * Intelisis (sus dos almacenes). Los lotes con sufijo de Intelisis ("2503353 SCC") cuentan como el mismo lote.
 * haeroPiezas es null si Almacén tiene cajas y no se sabe cuántas piezas trae la caja.
 */
public record LoteConciliadoResponse(String lote, LocalDate caducidad, Integer almacenCajas, Integer farmaciaPiezas,
                                     Integer haeroPiezas, Integer intelisisPiezas, List<String> lotesIntelisis) {
}
