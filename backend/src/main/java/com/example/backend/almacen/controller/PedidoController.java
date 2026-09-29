package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.CancelarPedidoRequest;
import com.example.backend.almacen.dto.CrearPedidoRequest;
import com.example.backend.almacen.dto.PedidoResponse;
import com.example.backend.almacen.service.ServicioPedidos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/almacen/pedidos")
public class PedidoController {

    private final ServicioPedidos servicioPedidos;

    public PedidoController(ServicioPedidos servicioPedidos) {
        this.servicioPedidos = servicioPedidos;
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponse>> listar(@RequestParam(required = false) String estatus) {
        return ResponseEntity.ok(servicioPedidos.listarPedidos(estatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(servicioPedidos.obtenerPedido(id));
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@RequestBody CrearPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioPedidos.crearPedido(request));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponse> cancelar(@PathVariable Long id,
                                                    @RequestBody CancelarPedidoRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(servicioPedidos.cancelarPedido(id, request, authentication.getName()));
    }
}
