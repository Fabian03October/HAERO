package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.AlertaResponse;
import com.example.backend.almacen.service.ServicioRotacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/almacen")
public class RotacionController {

    private final ServicioRotacion servicioRotacion;

    public RotacionController(ServicioRotacion servicioRotacion) {
        this.servicioRotacion = servicioRotacion;
    }

    @GetMapping("/rotacion")
    public ResponseEntity<?> rotacion(@RequestParam(required = false) String clave) {
        if (clave == null || clave.isBlank()) {
            return ResponseEntity.ok(servicioRotacion.listar());
        }
        return ResponseEntity.ok(servicioRotacion.obtener(clave));
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<AlertaResponse>> alertas(@RequestParam(defaultValue = "true") boolean activas) {
        return ResponseEntity.ok(servicioRotacion.listarAlertas(activas));
    }
}
