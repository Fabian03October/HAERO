package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String rol;
    private boolean debeCambiarContrasena;
    private String nombreCompleto;
    // Versión de las políticas de uso que ya aceptó (null si nunca) y cuándo.
    private String versionPoliticasAceptada;
    private LocalDateTime fechaAceptacionPoliticas;
}