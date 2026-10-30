package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CamaCarrito {
    private String espacio;
    private int idIngreso;
    private String paciente;
    private String expediente;
    private List<LineaCarrito> lineas;
    // Ya no está en cama (alta, defunción, ingreso cancelado...) pero se le surtió algo hoy:
    // se decide si se saca del carrito o se deja en su hoja de baja.
    private boolean egresado;
    private String motivoEgreso;
}
