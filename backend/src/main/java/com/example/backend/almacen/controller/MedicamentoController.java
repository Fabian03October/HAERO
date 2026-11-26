package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.dto.SeguridadMedicamentoRequest;
import com.example.backend.almacen.service.ServicioMedicamentos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalogo de medicamentos. El catalogo se lee de Intelisis (diseno aprobado); en HAERO
 * no se dan de alta claves. Lo unico propio de HAERO son los codigos de barras (se asocian
 * al registrar la entrada) y las marcas de seguridad (alto riesgo y red fria).
 */
@RestController
@RequestMapping("/api/almacen/medicamentos")
public class MedicamentoController {

    private final ServicioMedicamentos servicioMedicamentos;

    public MedicamentoController(ServicioMedicamentos servicioMedicamentos) {
        this.servicioMedicamentos = servicioMedicamentos;
    }

    @GetMapping
    public ResponseEntity<List<MedicamentoResponse>> listar() {
        return ResponseEntity.ok(servicioMedicamentos.listar());
    }

    @GetMapping("/{clave}")
    public ResponseEntity<MedicamentoResponse> obtener(@PathVariable String clave) {
        return ResponseEntity.ok(servicioMedicamentos.obtener(clave));
    }

    /** Seguridad del paciente: Almacén marca alto riesgo (lista ISMP) y red fría; Farmacia lo ve al surtir. */
    @PutMapping("/{clave}/seguridad")
    public ResponseEntity<MedicamentoResponse> actualizarSeguridad(@PathVariable String clave, @RequestBody SeguridadMedicamentoRequest request,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(servicioMedicamentos.actualizarSeguridad(clave, request.getAltoRiesgo(), request.isRedFria(), authentication.getName()));
    }
}
