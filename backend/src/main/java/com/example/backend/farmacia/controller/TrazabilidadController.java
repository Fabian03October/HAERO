package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.LoteTrazableResponse;
import com.example.backend.farmacia.dto.TrazabilidadResponse;
import com.example.backend.farmacia.service.ServicioTrazabilidad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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
                                                          @RequestParam(required = false) String paciente,
                                                          @RequestParam(required = false) String receta,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                          Authentication authentication) {
        if (receta != null && !receta.isBlank()) {
            return ResponseEntity.ok(servicio.porReceta(receta, authentication.getName()));
        }
        if (paciente != null && !paciente.isBlank()) {
            return ResponseEntity.ok(servicio.porPaciente(paciente, desde, hasta, authentication.getName()));
        }
        if (expediente != null && !expediente.isBlank()) {
            return ResponseEntity.ok(servicio.porExpediente(expediente, desde, hasta, authentication.getName()));
        }
        return ResponseEntity.ok(servicio.porLote(lote, clave, desde, hasta, authentication.getName()));
    }

    // Lotes de un medicamento (clave o nombre) o de un número de lote, en SICOMED y en Intelisis.
    @GetMapping("/lotes")
    public ResponseEntity<List<LoteTrazableResponse>> lotes(@RequestParam String q) {
        return ResponseEntity.ok(servicio.buscarLotes(q));
    }
}
