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
}
