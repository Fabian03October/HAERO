package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResolverCodigoResponse {
    private String clave;
    private String nombre;
    private String proveedor;
}
