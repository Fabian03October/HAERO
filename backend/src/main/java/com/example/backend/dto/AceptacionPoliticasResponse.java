package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AceptacionPoliticasResponse {
    private String version;
    private LocalDateTime fecha;
}
