package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DespacharSolicitudRequest {
    private List<PartidaDespacho> partidas;
    private boolean parcial;
    // Ajuste HU15: permite saltar el bloqueo FEFO cuando el despachador lo
    // confirma explicitamente desde la pantalla. El lote vencido SIEMPRE se
    // sigue bloqueando (eso nunca es opcional); esto solo salta la regla de
    // "hay otro lote que caduca antes".
    private boolean confirmarFueraDeFefo;
    // HU15 CA2: obligatorio si de verdad se sale del orden FEFO; queda en el movimiento.
    private String motivoFueraDeFefo;
}
