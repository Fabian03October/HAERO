package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class ExpedienteExternoResponse {
    private Long id;
    private String tipo;
    private String sentido;
    private String institucion;
    private String clave;
    private String nombreGenerico;
    private Integer cajas;
    private Integer cajasDevueltas;
    private LocalDateTime fechaRegistro;
    private LocalDate fechaLimite;
    private LocalDateTime fechaCierre;
    // Estatus guardado: ACTIVO, DEVUELTO_PARCIAL, CERRADO, REGISTRADA o CONFIRMADA.
    private String estatus;
    // Estatus para mostrar: el guardado, o VENCIDO / POR_CERRAR calculados al consultar.
    private String estatusVigente;
    private String observaciones;
    private String registradoPor;
    private List<DocumentoExternoResponse> documentos;
    private List<MovimientoResponse> movimientos;
}
