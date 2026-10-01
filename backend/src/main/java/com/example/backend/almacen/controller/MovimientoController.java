package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.service.ServicioMovimientos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/almacen/movimientos")
public class MovimientoController {

    private final ServicioMovimientos servicioMovimientos;

    public MovimientoController(ServicioMovimientos servicioMovimientos) {
        this.servicioMovimientos = servicioMovimientos;
    }

    @GetMapping
    public ResponseEntity<List<MovimientoResponse>> listar(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicioMovimientos.listar(tipo, desde, hasta));
    }
}
