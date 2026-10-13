package com.example.backend.intelisis;

import com.example.backend.intelisis.dto.ComparacionClaveResponse;
import com.example.backend.intelisis.dto.EstadoIntelisisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Integracion de solo lectura con Intelisis. Queda bajo /api/almacen, asi que
 * consultan Almacen y Supervision, y solo Almacen puede lanzar la sincronizacion.
 */
@RestController
@RequestMapping("/api/almacen/intelisis")
public class IntelisisController {

    private final ServicioIntelisis servicioIntelisis;
    private final ServicioSincronizacionIntelisis sincronizacion;

    public IntelisisController(ServicioIntelisis servicioIntelisis, ServicioSincronizacionIntelisis sincronizacion) {
        this.servicioIntelisis = servicioIntelisis;
        this.sincronizacion = sincronizacion;
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoIntelisisResponse> estado() {
        return ResponseEntity.ok(servicioIntelisis.estado());
    }

    @GetMapping("/comparacion")
    public ResponseEntity<List<ComparacionClaveResponse>> comparacion() {
        return ResponseEntity.ok(servicioIntelisis.comparacion());
    }

    /** Lanza la sincronizacion en segundo plano; el avance se consulta en /estado. */
    @PostMapping("/sincronizar")
    public ResponseEntity<Map<String, String>> sincronizar() {
        try {
            if (!sincronizacion.lanzarManual()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", "Ya hay una sincronización en curso."));
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("mensaje", e.getMessage()));
        }
        return ResponseEntity.accepted().body(Map.of("mensaje", "Sincronización iniciada."));
    }
}
