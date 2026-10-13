package com.example.backend.almacen.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MedicamentoResponse {
    private String clave;
    private String nombreGenerico;
    private String presentacion;
    private Integer piezasPorCaja;
    private String descripcion;

    // Datos de Intelisis; vacíos en las claves capturadas a mano en HAERO.
    private String formaFarmaceutica;
    private String concentracion;
    private String grupoTerapeutico;
    private Boolean cuadroBasico;
    private String estatus;
    private String claveCucop;
    private String unidadCompra;
    private String unidadMinima;
    private String riesgo;
    private String principalIndicacion;
    private String contraindicaciones;
    private String descripcionCompleta;
    private String origen;
    private LocalDateTime sincronizadoEn;
}
