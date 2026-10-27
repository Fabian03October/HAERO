package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.ClavePendienteResponse;
import com.example.backend.farmacia.dto.EntregaEncontradaResponse;
import com.example.backend.farmacia.dto.EntregaResponse;
import com.example.backend.farmacia.dto.PacienteResponse;
import com.example.backend.farmacia.dto.RecetaIntelisisResponse;
import com.example.backend.farmacia.dto.RecetaResponse;
import com.example.backend.farmacia.dto.RegistrarEntregaRequest;
import com.example.backend.farmacia.service.ServicioDevoluciones;
import com.example.backend.farmacia.service.ServicioEntregas;
import com.example.backend.farmacia.service.ServicioPacientesIntelisis;
import com.example.backend.farmacia.service.ServicioRecetasIntelisis;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/** Dispensación: HU28 (entrega y paciente), HU29 (recetas parciales y no dispensadas). */
@RestController
@RequestMapping("/api/farmacia")
public class EntregasController {

    private final ServicioEntregas servicioEntregas;
    private final ServicioPacientesIntelisis servicioPacientes;
    private final ServicioDevoluciones servicioDevoluciones;
    private final ServicioRecetasIntelisis servicioRecetasIntelisis;

    public EntregasController(ServicioEntregas servicioEntregas,
                              ServicioPacientesIntelisis servicioPacientes,
                              ServicioDevoluciones servicioDevoluciones,
                              ServicioRecetasIntelisis servicioRecetasIntelisis) {
        this.servicioEntregas = servicioEntregas;
        this.servicioPacientes = servicioPacientes;
        this.servicioDevoluciones = servicioDevoluciones;
        this.servicioRecetasIntelisis = servicioRecetasIntelisis;
    }

    @GetMapping("/pacientes")
    public ResponseEntity<List<PacienteResponse>> pacientes(@RequestParam(required = false) String expediente,
                                                            @RequestParam(required = false) String nombre,
                                                            @RequestParam(required = false) String curp) {
        return ResponseEntity.ok(servicioPacientes.buscar(expediente, nombre, curp));
    }

    // Recetas firmadas del paciente en Intelisis, para elegir una sin teclear folio ni claves.
    @GetMapping("/pacientes/{expediente}/recetas")
    public ResponseEntity<List<RecetaIntelisisResponse>> recetasDelPaciente(@PathVariable String expediente) {
        return ResponseEntity.ok(servicioRecetasIntelisis.delPaciente(expediente));
    }

    @GetMapping("/recetas-intelisis/{folio}")
    public ResponseEntity<RecetaIntelisisResponse> recetaDeIntelisis(@PathVariable String folio) {
        return ResponseEntity.ok(servicioRecetasIntelisis.porFolio(folio));
    }

    @PostMapping("/entregas")
    public ResponseEntity<EntregaResponse> registrar(@RequestBody RegistrarEntregaRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioEntregas.registrar(request, authentication.getName()));
    }

    // HU34 CA1: por folio de la entrega, folio de la receta, expediente o lote.
    @GetMapping("/entregas")
    public ResponseEntity<List<EntregaEncontradaResponse>> buscarEntregas(@RequestParam String buscar) {
        return ResponseEntity.ok(servicioDevoluciones.buscar(buscar));
    }

    @GetMapping("/recetas")
    public ResponseEntity<List<RecetaResponse>> recetas(@RequestParam(defaultValue = "PARCIAL,NO_DISPENSADA") String estado) {
        return ResponseEntity.ok(servicioEntregas.recetas(Arrays.stream(estado.split(",")).map(String::trim).toList()));
    }

    @GetMapping("/recetas/claves-pendientes")
    public ResponseEntity<List<ClavePendienteResponse>> clavesPendientes() {
        return ResponseEntity.ok(servicioEntregas.clavesPendientes());
    }

    @GetMapping("/recetas/{folio}")
    public ResponseEntity<RecetaResponse> receta(@PathVariable String folio) {
        return ResponseEntity.ok(servicioEntregas.receta(folio));
    }
}
