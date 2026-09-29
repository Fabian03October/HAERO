package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartidaPedidoRequest {
    private String clave;
    private Integer cantidadEsperada;
}
