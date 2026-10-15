package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PartidaPedidoResponse {
    private String clave;
    private String nombreGenerico;
    // Lo esperado, según Intelisis (sin lo cancelado).
    private Integer cantidadEsperada;
    // Lo que Almacén recibió físicamente en HAERO.
    private Integer cantidadRecibida;
    private String cucop;
    // Lo que dice Intelisis: recibido allá y cancelado.
    private Integer recibidaIntelisis;
    private Integer canceladaIntelisis;
}
