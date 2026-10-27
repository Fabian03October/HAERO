package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.CorteDiaResponse;
import com.example.backend.farmacia.dto.MarcarBajaRequest;
import com.example.backend.farmacia.dto.VerificacionResponse;
import com.example.backend.farmacia.service.ServicioCortes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** HU31 (resumen del día para la baja en Intelisis) y HU32 (verificación). */
@RestController
@RequestMapping("/api/farmacia/cortes")
public class CortesController {

    private final ServicioCortes servicio;

    public CortesController(ServicioCortes servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<LocalDate>> pendientes() {
        return ResponseEntity.ok(servicio.diasPendientes());
    }

    @GetMapping("/{dia}")
    public ResponseEntity<CorteDiaResponse> resumen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(servicio.resumen(dia));
    }

    @GetMapping("/{dia}/csv")
    public ResponseEntity<byte[]> csv(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"farmacia-intelisis-" + dia + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(servicio.csv(dia).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{dia}/baja")
    public ResponseEntity<CorteDiaResponse> marcarDadoDeBaja(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                                                             @RequestBody(required = false) MarcarBajaRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(servicio.marcarDadoDeBaja(dia, request == null ? null : request.getFolioIntelisis(), authentication.getName()));
    }

    @PostMapping("/{dia}/verificar")
    public ResponseEntity<VerificacionResponse> verificar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia) {
        return ResponseEntity.ok(servicio.verificar(dia));
    }
}
