package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** HU26: lo que Almacén despachó a una solicitud y Farmacia no ha recibido. */
@Getter
@AllArgsConstructor
public class DespachoPorRecibirResponse {
    private Long solicitudId;
    private LocalDateTime fechaSolicitud;
    private String clave;
    private String medicamento;
    private Integer piezasPorCaja;
    private Integer cajasPedidas;
    private Integer cajasDespachadas;
    private List<PartidaPorRecibir> partidas;
    // Lo que Farmacia ya tiene de esa clave y dónde (para sugerir el mismo estante); null si es nueva en Farmacia.
    private Integer piezasEnFarmacia;
    private String ubicacionSugerida;
}
