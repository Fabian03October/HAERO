package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UsuarioResponse {
    private Long id;
    private String nombreCompleto;
    private String nombreUsuario;
    private String correo;
    private String rol;
    private boolean activo;
    private boolean debeCambiarContrasena;
}
