package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.QuimioterapiaDtos.EstadoQuimio;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.QuimioResponse;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.RegistrarQuimioRequest;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SesionesSolicitud;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SolicitudQuimio;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SolicitudQuimioDetalle;
import com.example.backend.farmacia.service.ServicioQuimioterapia;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Quimioterapia: las solicitudes de Oncología Médica leídas de Intelisis y su surtido con doble
 * verificación.
 */
@RestController
@RequestMapping("/api/farmacia/quimioterapia")
public class QuimioterapiaController {

    private final ServicioQuimioterapia servicio;

    public QuimioterapiaController(ServicioQuimioterapia servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<QuimioResponse>> recientes() {
        return ResponseEntity.ok(servicio.recientes());
    }

    // Sesiones ya surtidas de una solicitud (una solicitud puede cubrir varios ciclos).
    @GetMapping("/solicitudes/{folio}")
    public ResponseEntity<SesionesSolicitud> sesiones(@PathVariable String folio) {
        return ResponseEntity.ok(servicio.sesiones(folio));
    }

    // Solicitudes de Oncología Médica de Intelisis: POR_SURTIR, HOY, SURTIDAS o todas.
    @GetMapping("/intelisis")
    public ResponseEntity<List<SolicitudQuimio>> solicitudes(@RequestParam(required = false) String filtro,
                                                             @RequestParam(required = false) String buscar) {
        return ResponseEntity.ok(servicio.solicitudes(filtro, buscar));
    }

    // Una solicitud con su nota, las claves sugeridas y el ciclo anterior del paciente.
    @GetMapping("/intelisis/{folio}")
    public ResponseEntity<SolicitudQuimioDetalle> solicitud(@PathVariable String folio, Authentication authentication) {
        return ResponseEntity.ok(servicio.solicitud(folio, authentication.getName()));
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoQuimio> estado() {
        return ResponseEntity.ok(servicio.estado());
    }

    @PostMapping
    public ResponseEntity<QuimioResponse> registrar(@RequestBody RegistrarQuimioRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(request, authentication.getName()));
    }
}
