package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SugerenciaDespachoResponse {
    private Long solicitudId;
    private Integer cantidadPendiente;
    private List<SugerenciaPartida> partidas;
}
