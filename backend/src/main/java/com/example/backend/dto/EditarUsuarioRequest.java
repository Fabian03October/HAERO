package com.example.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditarUsuarioRequest {
    private String nombreCompleto;
    private String correo;
    private String rol;
}
