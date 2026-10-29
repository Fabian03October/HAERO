package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SolicitudResponse {
    private Long id;
    private String clave;
    private String nombreGenerico;
    private Integer cantidadSolicitada;
    private Integer cantidadAtendida;
    private String estatus;
    private LocalDateTime fecha;
    // Quién de Farmacia la hizo: nombre para mostrar y usuario (para marcar "mías").
    private String solicitadoPor;
    private String solicitadoPorUsuario;
    // HU40: solo si se canceló o rechazó.
    private String motivoCierre;
    private String cerradaPor;
    private LocalDateTime fechaCierre;
    // HU39: pedido al que pertenece (null en las de una sola clave anteriores).
    private Long pedidoId;
}
