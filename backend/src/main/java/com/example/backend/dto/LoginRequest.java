package com.example.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    private String nombreUsuario;
    private String contrasena;
}
