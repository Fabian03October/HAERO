package com.example.backend.dto;

import lombok.Getter;
import lombok.Setter;

/** Versión de las políticas de uso y privacidad que el usuario acaba de aceptar. */
@Getter
@Setter
public class AceptarPoliticasRequest {
    private String version;
}
