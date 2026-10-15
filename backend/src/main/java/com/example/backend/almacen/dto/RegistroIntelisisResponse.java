package com.example.backend.almacen.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un registro de Intelisis que Almacén puede elegir al recibir una caja (HU10) o al
 * dar de alta lo que ya está en los anaqueles (HU21). Lote, caducidad y cantidades
 * son los de Intelisis: en HAERO no se teclean.
 *
 * @param origen        COMPRA (Ent. Compra), INV (donación o traspaso) o EXISTENCIA (alta inicial)
 * @param idIntelisis   Compra.ID o Inv.ID; null en EXISTENCIA
 * @param movimiento    p. ej. "Ent. Compra Farmacia 5678"; "Existencia 25301" en el alta inicial
 * @param cajas         cajas según Intelisis; null si no se sabe cuántas piezas trae la caja
 * @param bloqueo       por qué no se puede recibir; null si se puede
 * @param loteHaeroId   lote de HAERO que ya salió de este registro, si lo hay
 */
public record RegistroIntelisisResponse(
        String origen,
        Integer idIntelisis,
        String movimiento,
        String referencia,
        LocalDate fecha,
        String clave,
        String lote,
        LocalDate caducidad,
        BigDecimal piezas,
        BigDecimal cajas,
        String proveedor,
        Long pedidoId,
        String numeroPedido,
        String contrato,
        String bloqueo,
        Long loteHaeroId
) {
}
