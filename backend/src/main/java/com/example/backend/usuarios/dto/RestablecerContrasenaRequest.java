package com.example.backend.usuarios.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestablecerContrasenaRequest {
    private String contrasenaTemporal;
}
