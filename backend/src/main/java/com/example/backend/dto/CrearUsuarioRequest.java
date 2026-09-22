package com.example.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearUsuarioRequest {
    private String nombreCompleto;
    private String nombreUsuario;
    private String correo;
    private String contrasenaTemporal;
    private String rol;
}
