package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CanjeRequest {
    private Long loteOrigenId;
    private String numeroLote;
    private LocalDate caducidad;
    private List<UbicacionCantidad> ubicaciones;
}
