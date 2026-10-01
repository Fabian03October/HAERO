package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.CaducadoResponse;
import com.example.backend.almacen.dto.CanjeRequest;
import com.example.backend.almacen.dto.CanjeResponse;
import com.example.backend.almacen.dto.MoverCaducadoRequest;
import com.example.backend.almacen.dto.MovimientoExternoRequest;
import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.service.ServicioMovimientosEspeciales;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Movimientos especiales de Almacen: canjes (HU18), caducados (HU19) y
 * prestamos/transferencias (HU20).
 */
@RestController
@RequestMapping("/api/almacen")
public class MovimientosEspecialesController {

    private final ServicioMovimientosEspeciales servicio;

    public MovimientosEspecialesController(ServicioMovimientosEspeciales servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/canjes")
    public ResponseEntity<List<CanjeResponse>> listarCanjes() {
        return ResponseEntity.ok(servicio.listarCanjes());
    }

    @PostMapping("/canjes")
    public ResponseEntity<CanjeResponse> registrarCanje(@RequestBody CanjeRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrarCanje(request, authentication.getName()));
    }

    @GetMapping("/caducados")
    public ResponseEntity<List<CaducadoResponse>> listarCaducados(@RequestParam(defaultValue = "false") boolean pendientes) {
        return ResponseEntity.ok(servicio.listarCaducados(pendientes));
    }

    @PostMapping("/caducados")
    public ResponseEntity<CaducadoResponse> moverACaducados(@RequestBody MoverCaducadoRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.moverACaducados(request.getLoteId(), authentication.getName()));
    }

    @GetMapping("/movimientos-externos")
    public ResponseEntity<List<MovimientoResponse>> listarExternos() {
        return ResponseEntity.ok(servicio.listarExternos());
    }

    @PostMapping("/movimientos-externos")
    public ResponseEntity<MovimientoResponse> registrarExterno(@RequestBody MovimientoExternoRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrarExterno(request, authentication.getName()));
    }
}
