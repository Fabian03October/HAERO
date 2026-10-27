package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HU32: lo entregado en HAERO contra lo dado de baja en Intelisis. */
@Getter
@AllArgsConstructor
public class VerificacionResponse {
    private LocalDate dia;
    private String estado;
    // Última sincronización correcta con Intelisis (de ahí salen sus datos).
    private LocalDateTime sincronizadoEn;
    private List<RenglonVerificacionResponse> renglones;
}
