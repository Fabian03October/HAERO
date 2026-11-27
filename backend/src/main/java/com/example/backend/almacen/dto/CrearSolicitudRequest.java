package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearSolicitudRequest {
    private String clave;
    private Integer cantidad;
    // CAJA (por omisión) o PAQUETE: con PAQUETE, cantidad son paquetes.
    private String unidad;
}
