package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class RotacionResponse {
    private String clave;
    private String nombreGenerico;
    private double cpm;
    // SISTEMA = salidas registradas; HOJA = columna CONSUMO PROM. de la carga inicial.
    private String origenCpm;
    private int mesesHistorial;
    private int existenciaActual;
    // Lo que sí se alcanza a usar antes de que caduque cada lote al ritmo del CPM (salen
    // primero los que caducan antes). El desabasto se mide con esto, no con la existencia.
    private int existenciaAprovechable;
    // Cajas que caducarían sin usarse (existenciaActual - existenciaAprovechable).
    private int cajasEnRiesgoDeMerma;
    private double stockMinimo;
    private double stockMaximo;
    // DESABASTO, SOBREABASTO, NORMAL o SIN_CONSUMO.
    private String estado;
    private List<ConsumoMesResponse> meses;
}
