package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.DespacharSolicitudRequest;
import com.example.backend.almacen.dto.DespacharSolicitudResponse;
import com.example.backend.almacen.dto.SolicitudResponse;
import com.example.backend.almacen.dto.SugerenciaDespachoResponse;
import com.example.backend.almacen.service.ServicioDespacho;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/almacen/solicitudes")
public class SolicitudAlmacenController {

    private final ServicioDespacho servicioDespacho;

    public SolicitudAlmacenController(ServicioDespacho servicioDespacho) {
        this.servicioDespacho = servicioDespacho;
    }

    @GetMapping
    public ResponseEntity<List<SolicitudResponse>> bandeja(@RequestParam(defaultValue = "PENDIENTE,PARCIAL") String estatus) {
        List<String> estatuses = Arrays.asList(estatus.split(","));
        return ResponseEntity.ok(servicioDespacho.listarBandeja(estatuses));
    }

    @GetMapping("/{id}/sugerencia")
    public ResponseEntity<SugerenciaDespachoResponse> sugerencia(@PathVariable Long id) {
        return ResponseEntity.ok(servicioDespacho.sugerir(id));
    }

    // HU39: despacha todas las claves abiertas del pedido con la sugerencia FEFO, en una transacción.
    @PostMapping("/pedidos/{pedidoId}/despachar")
    public ResponseEntity<com.example.backend.almacen.dto.DespachoPedidoResponse> despacharPedido(
            @PathVariable Long pedidoId, @RequestParam(defaultValue = "false") boolean parcial, Authentication authentication) {
        return ResponseEntity.ok(servicioDespacho.despacharPedido(pedidoId, parcial, authentication.getName()));
    }

    // HU40: lo que Almacén no puede surtir, con su motivo.
    @PostMapping("/{id}/rechazar")
    public ResponseEntity<SolicitudResponse> rechazar(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> cuerpo,
                                                      Authentication authentication) {
        return ResponseEntity.ok(servicioDespacho.rechazar(id, cuerpo == null ? null : cuerpo.get("motivo"), authentication.getName()));
    }

    @PostMapping("/{id}/despachar")
    public ResponseEntity<DespacharSolicitudResponse> despachar(@PathVariable Long id,
                                                                 @RequestBody DespacharSolicitudRequest request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(servicioDespacho.despachar(id, request, authentication.getName()));
    }
}
