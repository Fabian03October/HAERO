package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.HojaBajaResponse;
import com.example.backend.farmacia.service.ServicioBajaArea;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Baja por volumen de Quimioterapia y Quirófano (área = QUIMIO o QUIROFANO). Las hojas se imprimen
 * con las de Hospitalización: /api/farmacia/carrito/bajas-volumen y /hojas-baja con central = área.
 */
@RestController
@RequestMapping("/api/farmacia/bajas-area/{area}")
public class BajaAreaController {

    private final ServicioBajaArea servicio;

    public BajaAreaController(ServicioBajaArea servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<ServicioBajaArea.EstadoBajaArea> estado(@PathVariable String area) {
        return ResponseEntity.ok(servicio.estado(area));
    }

    @PostMapping("/terminar")
    public ResponseEntity<List<HojaBajaResponse>> terminar(@PathVariable String area, Authentication authentication) {
        return ResponseEntity.ok(servicio.terminar(area, authentication.getName()));
    }
}
