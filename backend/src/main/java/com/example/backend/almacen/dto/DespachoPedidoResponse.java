package com.example.backend.almacen.dto;

import java.util.List;

/** HU39: resultado de despachar un pedido completo con la sugerencia FEFO. */
public record DespachoPedidoResponse(Long pedidoId, List<DespacharSolicitudResponse> despachadas, List<String> sinExistencia) {
}
