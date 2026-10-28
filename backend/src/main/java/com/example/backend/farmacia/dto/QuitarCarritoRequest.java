package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

/** Sacar del carrito lo que se metió (por ejemplo, porque el médico lo canceló): regresa al inventario. */
@Getter
@Setter
public class QuitarCarritoRequest {
    private Integer idIngreso;
    private String clave;
    private String motivo;
}
