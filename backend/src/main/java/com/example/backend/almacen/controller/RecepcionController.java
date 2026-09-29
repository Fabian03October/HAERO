package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.AsociarCodigoRequest;
import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistrarEntradaResponse;
import com.example.backend.almacen.dto.ResolverCodigoResponse;
import com.example.backend.almacen.service.ServicioRecepcion;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/almacen")
public class RecepcionController {

    private final ServicioRecepcion servicioRecepcion;

    public RecepcionController(ServicioRecepcion servicioRecepcion) {
        this.servicioRecepcion = servicioRecepcion;
    }

    @GetMapping("/codigos-barras/{codigo}")
    public ResponseEntity<ResolverCodigoResponse> resolverCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(servicioRecepcion.resolverCodigo(codigo));
    }

    @PostMapping("/codigos-barras")
    public ResponseEntity<Void> asociarCodigo(@RequestBody AsociarCodigoRequest request) {
        servicioRecepcion.asociarCodigo(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/entradas")
    public ResponseEntity<RegistrarEntradaResponse> registrarEntrada(@RequestBody RegistrarEntradaRequest request,
                                                                      Authentication authentication) {
        RegistrarEntradaResponse response = servicioRecepcion.registrarEntrada(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
