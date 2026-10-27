package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.AltaInicialRequest;
import com.example.backend.farmacia.dto.AltaInicialResponse;
import com.example.backend.farmacia.dto.BajaResponse;
import com.example.backend.farmacia.dto.LoteFarmaciaResponse;
import com.example.backend.farmacia.dto.RegistrarBajaRequest;
import com.example.backend.farmacia.service.ServicioInventarioFarmacia;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Inventario de Farmacia: HU25 (alta inicial), HU27 (existencias), HU30 (lote FEFO) y HU35 (bajas). */
@RestController
@RequestMapping("/api/farmacia")
public class InventarioFarmaciaController {

    private final ServicioInventarioFarmacia servicio;

    public InventarioFarmaciaController(ServicioInventarioFarmacia servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/existencias")
    public ResponseEntity<List<LoteFarmaciaResponse>> existencias() {
        return ResponseEntity.ok(servicio.existencias());
    }

    @GetMapping("/existencias/fefo")
    public ResponseEntity<LoteFarmaciaResponse> loteFefo(@RequestParam String clave) {
        return servicio.loteFefo(clave).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/alta-inicial")
    public ResponseEntity<AltaInicialResponse> altaInicial(@RequestBody AltaInicialRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.altaInicial(request, authentication.getName()));
    }

    @GetMapping("/bajas")
    public ResponseEntity<List<BajaResponse>> bajas() {
        return ResponseEntity.ok(servicio.bajas());
    }

    @GetMapping("/bajas/sugeridas")
    public ResponseEntity<List<LoteFarmaciaResponse>> sugeridas() {
        return ResponseEntity.ok(servicio.sugeridosParaBaja());
    }

    @PostMapping("/bajas")
    public ResponseEntity<BajaResponse> registrarBaja(@RequestBody RegistrarBajaRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrarBaja(request, authentication.getName()));
    }
}
