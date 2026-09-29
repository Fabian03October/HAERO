package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CrearPedidoRequest {
    private String numeroPedido;
    private LocalDate fecha;
    private String proveedor;
    private List<PartidaPedidoRequest> partidas;
}
