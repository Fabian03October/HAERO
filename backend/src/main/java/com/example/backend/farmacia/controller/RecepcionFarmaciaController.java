package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.DespachoPorRecibirResponse;
import com.example.backend.farmacia.dto.RecepcionResponse;
import com.example.backend.farmacia.dto.RecibirDespachoRequest;
import com.example.backend.farmacia.service.ServicioRecepcionFarmacia;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HU26: recepción en Farmacia de los despachos de Almacén. */
@RestController
@RequestMapping("/api/farmacia/recepciones")
public class RecepcionFarmaciaController {

    private final ServicioRecepcionFarmacia servicio;

    public RecepcionFarmaciaController(ServicioRecepcionFarmacia servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<DespachoPorRecibirResponse>> pendientes() {
        return ResponseEntity.ok(servicio.porRecibir());
    }

    // Almacén también la consulta para ver las diferencias (HU26 CA2).
    @GetMapping
    public ResponseEntity<List<RecepcionResponse>> recepciones(@RequestParam(defaultValue = "false") boolean conDiferencia) {
        return ResponseEntity.ok(servicio.recepciones(conDiferencia));
    }

    @PostMapping
    public ResponseEntity<RecepcionResponse> recibir(@RequestBody RecibirDespachoRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.recibir(request, authentication.getName()));
    }
}
