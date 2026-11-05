package com.example.backend.farmacia.controller;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.service.ServicioMedicamentos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Seguridad del paciente: Farmacia marca qué claves son de alto riesgo (lista ISMP) y cuáles son
 * de red fría. La consulta va con el catálogo (GET /api/almacen/medicamentos).
 */
@RestController
@RequestMapping("/api/farmacia/medicamentos")
public class SeguridadMedicamentoController {

    /** altoRiesgo null = usar lo que dice Intelisis. */
    public record SeguridadRequest(Boolean altoRiesgo, boolean redFria) {
    }

    private final ServicioMedicamentos servicio;

    public SeguridadMedicamentoController(ServicioMedicamentos servicio) {
        this.servicio = servicio;
    }

    @PutMapping("/{clave}/seguridad")
    public ResponseEntity<MedicamentoResponse> actualizar(@PathVariable String clave, @RequestBody SeguridadRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(servicio.actualizarSeguridad(clave, request.altoRiesgo(), request.redFria(), authentication.getName()));
    }
}
