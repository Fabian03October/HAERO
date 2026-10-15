package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PedidoResponse {
    private Long id;
    private String numeroPedido;
    private String proveedor;
    private String estatus;
    private LocalDate fecha;
    private List<PartidaPedidoResponse> partidas;
    private String motivoCancelacion;
    // Datos de Intelisis: contrato, entrega comprometida y estatus allá.
    private String origen;
    private String contrato;
    private LocalDate fechaEntrega;
    private String estatusIntelisis;
    private LocalDateTime fechaCancelacion;
}
