package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.PedidoResponse;
import com.example.backend.almacen.service.ServicioPedidos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pedidos de Intelisis, solo consulta (HU22 y HU11). En HAERO ya no se capturan ni se
 * cancelan pedidos: eso se hace en Intelisis y llega con la sincronizacion.
 */
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
}
