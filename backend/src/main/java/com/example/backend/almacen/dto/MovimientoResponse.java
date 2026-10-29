package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MovimientoResponse {
    private Long id;
    private String tipo;
    private LocalDateTime fecha;
    private String usuario;
    private Long solicitudId;
    private String clave;
    private String nombreGenerico;
    private String numeroLote;
    private LocalDate caducidad;
    private String ubicacion;
    private Integer cajas;
    private String sentido;
    private String institucion;
    // Solo salidas a Farmacia: quien de Farmacia hizo la solicitud, cuando y cuanto pidio.
    private String solicitadoPor;
    private LocalDateTime fechaSolicitud;
    private Integer cantidadSolicitada;
    // Préstamos: true si el movimiento es la devolución (va en sentido contrario al préstamo).
    private boolean devolucion;
    // Por qué se hizo (por ejemplo, despacho fuera de FEFO), si la regla lo pidió.
    private String motivo;
    // HU39: pedido de Farmacia al que pertenece la salida (para su vale).
    private Long pedidoId;
}
