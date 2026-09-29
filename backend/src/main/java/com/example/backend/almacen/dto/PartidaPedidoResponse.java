package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PartidaPedidoResponse {
    private String clave;
    private String nombreGenerico;
    private Integer cantidadEsperada;
    private Integer cantidadRecibida;
}
