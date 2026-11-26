package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

/** Marca de seguridad de una clave. altoRiesgo null = usar lo que dice Intelisis. */
@Getter
@Setter
public class SeguridadMedicamentoRequest {
    private Boolean altoRiesgo;
    private boolean redFria;
}
