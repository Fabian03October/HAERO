package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.DevolucionResponse;
import com.example.backend.farmacia.dto.RegistrarDevolucionRequest;
import com.example.backend.farmacia.service.ServicioDevoluciones;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HU34: devoluciones a Farmacia. */
@RestController
@RequestMapping("/api/farmacia/devoluciones")
public class DevolucionesController {

    private final ServicioDevoluciones servicio;

    public DevolucionesController(ServicioDevoluciones servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<List<DevolucionResponse>> registrar(@RequestBody RegistrarDevolucionRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(request, authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<DevolucionResponse>> delDia(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(servicio.delDia(dia == null ? LocalDate.now() : dia));
    }
}
