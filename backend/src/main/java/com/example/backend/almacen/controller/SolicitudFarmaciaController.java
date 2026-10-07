package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.CrearSolicitudRequest;
import com.example.backend.almacen.dto.SolicitudResponse;
import com.example.backend.almacen.service.ServicioDespacho;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farmacia/solicitudes")
public class SolicitudFarmaciaController {

    private final ServicioDespacho servicioDespacho;

    public SolicitudFarmaciaController(ServicioDespacho servicioDespacho) {
        this.servicioDespacho = servicioDespacho;
    }

    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@RequestBody CrearSolicitudRequest request, Authentication authentication) {
        SolicitudResponse respuesta = servicioDespacho.crearSolicitud(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping
    public ResponseEntity<List<SolicitudResponse>> listar() {
        // Todas las solicitudes del área de Farmacia, no solo las del usuario.
        return ResponseEntity.ok(servicioDespacho.listarSolicitudesDeFarmacia());
    }
}
