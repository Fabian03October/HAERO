package com.example.backend.almacen.controller;

import com.example.backend.almacen.service.ServicioBajaDefinitiva;
import com.example.backend.almacen.service.ServicioBajaDefinitiva.BajaDefinitiva;
import com.example.backend.almacen.service.ServicioBajaDefinitiva.RegistrarBajaDefinitiva;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Baja definitiva de caducados con el acta en PDF. El alta es multipart: "datos" (JSON) y "documento" (PDF). */
@RestController
@RequestMapping("/api/almacen/caducados/bajas-definitivas")
public class BajaDefinitivaController {

    private final ServicioBajaDefinitiva servicio;

    public BajaDefinitivaController(ServicioBajaDefinitiva servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<BajaDefinitiva>> listar() {
        return ResponseEntity.ok(servicio.listar());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BajaDefinitiva> registrar(@RequestPart("datos") RegistrarBajaDefinitiva datos,
                                                    @RequestPart(value = "documento", required = false) MultipartFile documento,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(datos, documento, authentication.getName()));
    }

    @GetMapping("/{id}/documento")
    public ResponseEntity<byte[]> documento(@PathVariable Long id) {
        ServicioBajaDefinitiva.Documento documento = servicio.documento(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(documento.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(documento.contenido());
    }
}
