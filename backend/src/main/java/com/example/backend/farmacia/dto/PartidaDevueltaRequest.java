package com.example.backend.farmacia.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartidaDevueltaRequest {
    private Long partidaId;
    private Integer piezas;
    private boolean danado;
}
