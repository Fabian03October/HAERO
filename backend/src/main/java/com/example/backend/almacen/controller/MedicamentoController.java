package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.CrearMedicamentoRequest;
import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.service.ServicioMedicamentos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/almacen/medicamentos")
public class MedicamentoController {

    private final ServicioMedicamentos servicioMedicamentos;

    public MedicamentoController(ServicioMedicamentos servicioMedicamentos) {
        this.servicioMedicamentos = servicioMedicamentos;
    }

    @GetMapping
    public ResponseEntity<List<MedicamentoResponse>> listar() {
        return ResponseEntity.ok(servicioMedicamentos.listar());
    }

    @GetMapping("/{clave}")
    public ResponseEntity<MedicamentoResponse> obtener(@PathVariable String clave) {
        return ResponseEntity.ok(servicioMedicamentos.obtener(clave));
    }

    @PostMapping
    public ResponseEntity<MedicamentoResponse> crear(@RequestBody CrearMedicamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioMedicamentos.crear(request));
    }
}
