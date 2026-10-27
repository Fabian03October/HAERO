package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.TrazabilidadResponse;
import com.example.backend.farmacia.service.ServicioTrazabilidad;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** HU33: trazabilidad de lote a paciente (y de paciente a lotes). */
@RestController
@RequestMapping("/api/farmacia/trazabilidad")
public class TrazabilidadController {

    private final ServicioTrazabilidad servicio;

    public TrazabilidadController(ServicioTrazabilidad servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<TrazabilidadResponse> consultar(@RequestParam(required = false) String lote,
                                                          @RequestParam(required = false) String clave,
                                                          @RequestParam(required = false) String expediente,
                                                          Authentication authentication) {
        if (expediente != null && !expediente.isBlank()) {
            return ResponseEntity.ok(servicio.porExpediente(expediente, authentication.getName()));
        }
        return ResponseEntity.ok(servicio.porLote(lote, clave, authentication.getName()));
    }
}
