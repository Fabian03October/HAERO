package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsociarCodigoRequest {
    private String codigo;
    private String clave;
    private String proveedor;
}
