package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.DevolucionPrestamoRequest;
import com.example.backend.almacen.dto.ExpedienteExternoResponse;
import com.example.backend.almacen.dto.RegistrarExpedienteRequest;
import com.example.backend.almacen.entity.DocumentoExterno;
import com.example.backend.almacen.service.ServicioExpedientesExternos;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Préstamos y transferencias con expediente y documentos PDF (ajuste HU20).
 * Las altas y el cierre son multipart: parte "datos" (JSON) y parte "documento" (PDF).
 */
@RestController
@RequestMapping("/api/almacen/expedientes-externos")
public class ExpedientesExternosController {

    private final ServicioExpedientesExternos servicio;

    public ExpedientesExternosController(ServicioExpedientesExternos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<ExpedienteExternoResponse>> listar() {
        return ResponseEntity.ok(servicio.listar());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExpedienteExternoResponse> registrar(@RequestPart("datos") RegistrarExpedienteRequest datos,
                                                               @RequestPart(value = "documento", required = false) MultipartFile documento,
                                                               Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(datos, documento, authentication.getName()));
    }

    @PostMapping("/{id}/devoluciones")
    public ResponseEntity<ExpedienteExternoResponse> registrarDevolucion(@PathVariable Long id,
                                                                         @RequestBody DevolucionPrestamoRequest request,
                                                                         Authentication authentication) {
        return ResponseEntity.ok(servicio.registrarDevolucion(id, request, authentication.getName()));
    }

    @PostMapping(path = "/{id}/cierre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExpedienteExternoResponse> cerrar(@PathVariable Long id,
                                                            @RequestPart(value = "documento", required = false) MultipartFile documento,
                                                            Authentication authentication) {
        return ResponseEntity.ok(servicio.cerrar(id, documento, authentication.getName()));
    }

    @GetMapping("/{id}/documentos/{documentoId}")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, @PathVariable Long documentoId) {
        DocumentoExterno documento = servicio.documento(id, documentoId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(documento.getNombreArchivo(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(documento.getContenido());
    }
}
