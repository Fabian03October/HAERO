package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** HU33: recorrido de un lote del pedido al paciente, o los lotes que recibió un paciente. */
@Getter
@AllArgsConstructor
public class TrazabilidadResponse {
    private String modo;
    private String valor;
    private List<PasoRecorridoResponse> recorrido;
    private List<EntregaPacienteResponse> entregas;
}
