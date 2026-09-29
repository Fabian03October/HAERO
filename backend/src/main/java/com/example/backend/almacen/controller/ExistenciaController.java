package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.ExistenciaResponse;
import com.example.backend.almacen.service.ServicioDespacho;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/almacen/existencias")
public class ExistenciaController {

    private final ServicioDespacho servicioDespacho;

    public ExistenciaController(ServicioDespacho servicioDespacho) {
        this.servicioDespacho = servicioDespacho;
    }

    @GetMapping
    public ResponseEntity<List<ExistenciaResponse>> listar(@RequestParam(required = false) String clave,
                                                            @RequestParam(required = false) String lote,
                                                            @RequestParam(required = false) String ubicacion) {
        return ResponseEntity.ok(servicioDespacho.listarExistencias(clave, lote, ubicacion));
    }
}
