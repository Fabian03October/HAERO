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

    // HU39: varias claves en un solo pedido.
    @PostMapping("/pedidos")
    public ResponseEntity<List<SolicitudResponse>> crearPedido(@RequestBody com.example.backend.almacen.dto.CrearPedidoRequest request,
                                                               Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioDespacho.crearPedido(request, authentication.getName()));
    }

    @PostMapping("/pedidos/{pedidoId}/cancelar")
    public ResponseEntity<List<SolicitudResponse>> cancelarPedido(@PathVariable Long pedidoId,
                                                                  @RequestBody(required = false) java.util.Map<String, String> cuerpo,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(servicioDespacho.cancelarPedido(pedidoId, cuerpo == null ? null : cuerpo.get("motivo"), authentication.getName()));
    }

    // HU40: Farmacia cancela lo que ya no necesita.
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<SolicitudResponse> cancelar(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> cuerpo,
                                                      Authentication authentication) {
        return ResponseEntity.ok(servicioDespacho.cancelar(id, cuerpo == null ? null : cuerpo.get("motivo"), authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<SolicitudResponse>> listar() {
        // Todas las solicitudes del área de Farmacia, no solo las del usuario.
        return ResponseEntity.ok(servicioDespacho.listarSolicitudesDeFarmacia());
    }
}
