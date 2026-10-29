package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PartidaAltaInicial {
    private String clave;
    private String lote;
    private LocalDate caducidad;
    private Integer piezas;
    // HU42: código de barras escaneado de la caja; se liga a la clave si es nuevo.
    private String codigo;
}
