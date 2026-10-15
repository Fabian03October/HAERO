package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.PartidaPedidoResponse;
import com.example.backend.almacen.dto.PedidoResponse;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Pedidos (HU22) y pedidos cancelados (HU11), solo consulta. Los pedidos se leen de
 * Intelisis (diseno aprobado): ahi se capturan y se cancelan. HAERO solo guarda lo que
 * Almacen recibio fisicamente contra cada pedido, al registrar la entrada (HU10).
 */
@Service
public class ServicioPedidos {

    private final PedidoRepository pedidoRepository;

    public ServicioPedidos(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<PedidoResponse> listarPedidos(String estatus) {
        List<Pedido> pedidos = (estatus == null || estatus.isBlank())
                ? pedidoRepository.findAll()
                : pedidoRepository.findByEstatus(estatus);

        return pedidos.stream().map(this::aRespuesta).toList();
    }

    public PedidoResponse obtenerPedido(Long id) {
        return aRespuesta(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado")));
    }

    private PedidoResponse aRespuesta(Pedido pedido) {
        List<PartidaPedidoResponse> partidas = pedido.getPartidas().stream()
                .map(d -> new PartidaPedidoResponse(
                        d.getMedicamento().getClave(),
                        d.getMedicamento().getNombreGenerico(),
                        d.getCantidadEsperada(),
                        d.getCantidadRecibida(),
                        d.getCucop(),
                        d.getRecibidaIntelisis(),
                        d.getCanceladaIntelisis()))
                .toList();

        return new PedidoResponse(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getProveedor(),
                pedido.getEstatus(),
                pedido.getFecha(),
                partidas,
                pedido.getMotivoCancelacion(),
                pedido.getOrigen(),
                pedido.getContrato(),
                pedido.getFechaEntrega(),
                pedido.getEstatusIntelisis(),
                pedido.getFechaCancelacion()
        );
    }
}
