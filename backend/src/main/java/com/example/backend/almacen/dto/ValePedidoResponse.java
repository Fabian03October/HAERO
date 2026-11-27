package com.example.backend.almacen.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vale de un pedido de Farmacia: lo que Almacén entregó (lote, caducidad, ubicación, cajas, quién y cuándo) y lo que
 * no se surtió (clave, medicamento, cuánto faltó y por qué). Lo imprimen Almacén y Farmacia, al despachar o después.
 */
public record ValePedidoResponse(Long pedidoId, String solicitadoPor, LocalDateTime fechaPedido,
                                 List<Entregado> entregado, List<NoSurtido> noSurtido) {

    public record Entregado(String clave, String medicamento, String lote, LocalDate caducidad, String ubicacion, int cajas,
                            String despacho, LocalDateTime fecha, Integer cajasPorPaquete) {
    }

    /** estatus: PARCIAL o PENDIENTE (falta), RECHAZADA (Almacén no la surtirá) o CANCELADA (Farmacia ya no la quiso). */
    public record NoSurtido(String clave, String medicamento, int pedidas, int surtidas, int faltan, String motivo, String estatus,
                            Integer paquetes, Integer cajasPorPaquete) {
    }
}
