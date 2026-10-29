package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** HU39: varias claves en un solo pedido de Farmacia. */
@Getter
@Setter
public class CrearPedidoRequest {
    private List<CrearSolicitudRequest> partidas;
    private String observaciones;
}
