package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CanjeResponse {
    private LocalDateTime fecha;
    private String usuario;
    private String clave;
    private String nombreGenerico;
    private String proveedor;
    private Long loteOrigenId;
    private String loteOrigen;
    private LocalDate caducidadOrigen;
    private Integer cajasOrigen;
    private Long loteNuevoId;
    private String loteNuevo;
    private LocalDate caducidadNueva;
    private Integer cajasNuevas;
    private String ubicacion;
}
