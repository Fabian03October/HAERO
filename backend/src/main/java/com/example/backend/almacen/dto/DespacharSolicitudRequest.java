package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DespacharSolicitudRequest {
    private List<PartidaDespacho> partidas;
    private boolean parcial;
}
