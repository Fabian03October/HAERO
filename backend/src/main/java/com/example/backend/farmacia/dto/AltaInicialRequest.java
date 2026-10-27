package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** HU25: lo contado en los estantes de Farmacia. */
@Getter
@Setter
public class AltaInicialRequest {
    private List<PartidaAltaInicial> partidas;
}
