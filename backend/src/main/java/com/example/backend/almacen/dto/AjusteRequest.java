package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

/** HU36: ajuste por conteo físico de un lote en una ubicación. */
@Getter
@Setter
public class AjusteRequest {
    private Long existenciaId;
    private Integer cajasContadas;
    // ERROR_CONTEO, DANADA, FALTANTE o SOBRANTE.
    private String motivo;
    private String detalle;
}
