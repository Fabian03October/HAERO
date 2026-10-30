package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.CarritoResponse;
import com.example.backend.farmacia.dto.EscanearCarritoRequest;
import com.example.backend.farmacia.dto.EscaneoCarritoResponse;
import com.example.backend.farmacia.dto.HojaBajaResponse;
import com.example.backend.farmacia.dto.HojaIndicacionesResponse;
import com.example.backend.farmacia.dto.QuitarCarritoRequest;
import com.example.backend.farmacia.dto.SurtidoCarritoResponse;
import com.example.backend.farmacia.dto.SurtirCarritoRequest;
import com.example.backend.farmacia.service.ServicioCarrito;
import com.example.backend.farmacia.service.ServicioHojasCarrito;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Carrito del día de Hospitalización: lista por área, escaneo, surtido, sacar del carrito,
 * terminar y lo que se imprime (indicaciones médicas antes; hoja de baja al terminar).
 */
@RestController
@RequestMapping("/api/farmacia/carrito")
public class CarritoController {

    private final ServicioCarrito servicio;
    private final ServicioHojasCarrito hojas;

    public CarritoController(ServicioCarrito servicio, ServicioHojasCarrito hojas) {
        this.servicio = servicio;
        this.hojas = hojas;
    }

    @GetMapping
    public ResponseEntity<CarritoResponse> carrito(@RequestParam String central) {
        return ResponseEntity.ok(servicio.carrito(central));
    }

    @PostMapping("/escanear")
    public ResponseEntity<EscaneoCarritoResponse> escanear(@RequestBody EscanearCarritoRequest request) {
        return ResponseEntity.ok(servicio.escanear(request));
    }

    @PostMapping("/surtir")
    public ResponseEntity<SurtidoCarritoResponse> surtir(@RequestBody SurtirCarritoRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.surtir(request, authentication.getName()));
    }

    // Sacar del carrito lo que se metió (por ejemplo, porque se canceló): regresa al inventario.
    @PostMapping("/quitar")
    public ResponseEntity<CarritoResponse> quitar(@RequestBody QuitarCarritoRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.quitar(request, authentication.getName()));
    }

    // "Indicaciones médicas activas" para imprimir: todas las del área o los pacientes elegidos.
    @GetMapping("/indicaciones")
    public ResponseEntity<List<HojaIndicacionesResponse>> indicaciones(@RequestParam String central,
                                                                       @RequestParam(required = false) List<Integer> ingresos) {
        return ResponseEntity.ok(hojas.indicaciones(central, ingresos));
    }

    // Termina el carrito (del día o de urgencia): regresa la baja por volumen para capturar en Intelisis.
    @PostMapping("/terminar")
    public ResponseEntity<List<HojaBajaResponse>> terminar(@RequestParam String central,
                                                           @RequestParam(defaultValue = "false") boolean confirmarCancelados,
                                                           Authentication authentication) {
        return ResponseEntity.ok(hojas.terminar(central, confirmarCancelados, authentication.getName()));
    }

    @GetMapping("/bajas-volumen")
    public ResponseEntity<List<HojaBajaResponse>> bajasVolumen(@RequestParam String central,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                                                               @RequestParam(required = false) Integer folio) {
        return ResponseEntity.ok(hojas.bajasVolumen(central, dia == null ? LocalDate.now() : dia, folio));
    }

    @GetMapping("/hojas-baja")
    public ResponseEntity<List<HojaBajaResponse>> hojasBaja(@RequestParam String central,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dia,
                                                            @RequestParam(required = false) Integer ingreso) {
        return ResponseEntity.ok(hojas.hojasBaja(central, dia == null ? LocalDate.now() : dia, ingreso));
    }
}
