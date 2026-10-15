package com.example.backend.intelisis;

import com.example.backend.intelisis.dto.AvisoIntelisisResponse;
import com.example.backend.intelisis.dto.ComparacionClaveResponse;
import com.example.backend.intelisis.dto.EstadoIntelisisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    // ---------- Avisos de cambios en Intelisis (Supervision solo los consulta) ----------

    @GetMapping("/avisos")
    public ResponseEntity<List<AvisoIntelisisResponse>> avisos(@RequestParam(defaultValue = "true") boolean pendientes) {
        return ResponseEntity.ok(servicioIntelisis.avisos(pendientes));
    }

    @GetMapping("/avisos/resumen")
    public ResponseEntity<Map<String, Integer>> resumenAvisos() {
        return ResponseEntity.ok(servicioIntelisis.resumenAvisos());
    }

    @PostMapping("/avisos/{id}/revisado")
    public ResponseEntity<Map<String, String>> marcarRevisado(@PathVariable long id, Authentication authentication) {
        if (!servicioIntelisis.marcarRevisado(id, authentication.getName())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", "El aviso no existe o ya estaba revisado."));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Aviso marcado como revisado."));
    }

    @PostMapping("/avisos/informativos/revisados")
    public ResponseEntity<Map<String, String>> marcarInformativosRevisados(Authentication authentication) {
        int total = servicioIntelisis.marcarInformativosRevisados(authentication.getName());
        return ResponseEntity.ok(Map.of("mensaje", total + " avisos informativos marcados como revisados."));
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
