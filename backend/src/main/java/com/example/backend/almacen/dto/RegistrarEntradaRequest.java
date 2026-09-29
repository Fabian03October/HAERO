package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class RegistrarEntradaRequest {
    private Long pedidoId;
    private String clave;
    private String proveedor;
    private String numeroLote;
    private LocalDate caducidad;
    private List<UbicacionCantidad> ubicaciones;
}
