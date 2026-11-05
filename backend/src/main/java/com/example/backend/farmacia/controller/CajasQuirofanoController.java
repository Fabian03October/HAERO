package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.dto.CajasQuirofanoDtos.ArmarRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CajaDadaDeBaja;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CajaResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CodigoResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.EntregarRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.GuardarCajaRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.MotivoCajaRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.RecibirRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.SacarRequest;
import com.example.backend.farmacia.service.ServicioCajasQuirofano;
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

/**
 * Cajas de anestesia de quirófano: catálogo, armado, entrega (vale del turno) y devolución con
 * lo utilizado por paciente. La devolución es multipart: parte "datos" (JSON) y parte
 * "documento" (PDF de la indicación médica, obligatorio si se utilizó algo).
 */
@RestController
@RequestMapping("/api/farmacia/quirofano")
public class CajasQuirofanoController {

    private final ServicioCajasQuirofano servicio;

    public CajasQuirofanoController(ServicioCajasQuirofano servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/cajas")
    public ResponseEntity<List<CajaResponse>> listar() {
        return ResponseEntity.ok(servicio.listar());
    }

    @GetMapping("/cajas/{codigo}")
    public ResponseEntity<CajaResponse> detalle(@PathVariable String codigo) {
        return ResponseEntity.ok(servicio.detalle(codigo));
    }

    @PostMapping("/cajas")
    public ResponseEntity<CajaResponse> crear(@RequestBody GuardarCajaRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(request, authentication.getName()));
    }

    @PutMapping("/cajas/{codigo}")
    public ResponseEntity<CajaResponse> cambiar(@PathVariable String codigo, @RequestBody GuardarCajaRequest request,
                                                Authentication authentication) {
        return ResponseEntity.ok(servicio.cambiar(codigo, request, authentication.getName()));
    }

    // Dar de baja: no se borra (su historial es trazabilidad); lo que trae regresa al estante.
    @PostMapping("/cajas/{codigo}/baja")
    public ResponseEntity<Void> darDeBaja(@PathVariable String codigo, @RequestBody MotivoCajaRequest request,
                                          Authentication authentication) {
        servicio.darDeBaja(codigo, request, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cajas/{codigo}/reactivar")
    public ResponseEntity<CajaResponse> reactivar(@PathVariable String codigo, @RequestBody MotivoCajaRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.ok(servicio.reactivar(codigo, request, authentication.getName()));
    }

    @GetMapping("/cajas-de-baja")
    public ResponseEntity<List<CajaDadaDeBaja>> dadasDeBaja() {
        return ResponseEntity.ok(servicio.dadasDeBaja());
    }

    @PostMapping("/cajas/{codigo}/armar")
    public ResponseEntity<CajaResponse> armar(@PathVariable String codigo, @RequestBody ArmarRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.armar(codigo, request, authentication.getName()));
    }

    @PostMapping("/cajas/{codigo}/sacar")
    public ResponseEntity<CajaResponse> sacar(@PathVariable String codigo, @RequestBody SacarRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.sacar(codigo, request, authentication.getName()));
    }

    @PostMapping("/cajas/{codigo}/entregar")
    public ResponseEntity<CajaResponse> entregar(@PathVariable String codigo, @RequestBody EntregarRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(servicio.entregar(codigo, request, authentication.getName()));
    }

    @PostMapping(path = "/cajas/{codigo}/recibir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CajaResponse> recibir(@PathVariable String codigo,
                                                @RequestPart("datos") RecibirRequest datos,
                                                @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
                                                Authentication authentication) {
        return ResponseEntity.ok(servicio.recibir(codigo, datos, documentos, authentication.getName()));
    }

    // Médicos de Intelisis (anestesiólogos primero) para el vale.
    @GetMapping("/medicos")
    public ResponseEntity<List<com.example.backend.farmacia.dto.CajasQuirofanoDtos.MedicoResponse>> medicos(@RequestParam String nombre) {
        return ResponseEntity.ok(servicio.medicos(nombre));
    }

    // Piezas dentro de las cajas por lote (siguen siendo de Farmacia).
    @GetMapping("/en-cajas")
    public ResponseEntity<List<ServicioCajasQuirofano.PiezasEnCaja>> enCajas() {
        return ResponseEntity.ok(servicio.enCajas());
    }

    // Código de barras de un medicamento (para reconocerlo al armar la caja).
    @GetMapping("/codigo")
    public ResponseEntity<CodigoResponse> codigo(@RequestParam String codigo) {
        return ResponseEntity.ok(servicio.codigo(codigo));
    }

    @GetMapping("/vales/{id}/indicacion")
    public ResponseEntity<byte[]> indicacion(@PathVariable Long id, @RequestParam String expediente) {
        ServicioCajasQuirofano.Indicacion indicacion = servicio.indicacion(id, expediente);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(indicacion.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(indicacion.contenido());
    }
}
