package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BitacoraUsuarioResponse {
    private Long id;
    private LocalDateTime fecha;
    private String realizadoPor;
    private Long usuarioId;
    private String nombreUsuario;
    private String nombreCompleto;
    private String accion;
    private String detalle;
}
