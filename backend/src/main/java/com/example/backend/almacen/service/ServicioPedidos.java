package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CancelarPedidoRequest;
import com.example.backend.almacen.dto.CrearPedidoRequest;
import com.example.backend.almacen.dto.PartidaPedidoRequest;
import com.example.backend.almacen.dto.PartidaPedidoResponse;
import com.example.backend.almacen.dto.PedidoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.entity.PedidoDetalle;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.PedidoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ServicioPedidos {

    private final PedidoRepository pedidoRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicioPedidos(PedidoRepository pedidoRepository,
                            MedicamentoRepository medicamentoRepository,
                            UsuarioRepository usuarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public PedidoResponse crearPedido(CrearPedidoRequest request) {
        if (pedidoRepository.existsByNumeroPedido(request.getNumeroPedido())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya existe un pedido con ese número");
        }

        if (request.getPartidas() == null || request.getPartidas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido debe tener al menos una partida");
        }

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido(request.getNumeroPedido());
        pedido.setProveedor(request.getProveedor());
        pedido.setFecha(request.getFecha());
        pedido.setEstatus("ACTIVO");

        for (PartidaPedidoRequest partidaReq : request.getPartidas()) {
            Medicamento medicamento = medicamentoRepository.findById(partidaReq.getClave())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La clave " + partidaReq.getClave() + " no está registrada en el catálogo de medicamentos"));

            PedidoDetalle detalle = new PedidoDetalle();
            detalle.setPedido(pedido);
            detalle.setMedicamento(medicamento);
            detalle.setCantidadEsperada(partidaReq.getCantidadEsperada());
            detalle.setCantidadRecibida(0);
            pedido.getPartidas().add(detalle);
        }

        return aRespuesta(pedidoRepository.save(pedido));
    }

    public List<PedidoResponse> listarPedidos(String estatus) {
        List<Pedido> pedidos = (estatus == null || estatus.isBlank())
                ? pedidoRepository.findAll()
                : pedidoRepository.findByEstatus(estatus);

        return pedidos.stream().map(this::aRespuesta).toList();
    }

    public PedidoResponse obtenerPedido(Long id) {
        return aRespuesta(buscarPedido(id));
    }

    public PedidoResponse cancelarPedido(Long id, CancelarPedidoRequest request, String nombreUsuarioSolicitante) {
        if (request.getMotivo() == null || request.getMotivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo de cancelación es obligatorio");
        }

        Pedido pedido = buscarPedido(id);
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        pedido.setEstatus("CANCELADO");
        pedido.setMotivoCancelacion(request.getMotivo());
        pedido.setFechaCancelacion(LocalDateTime.now());
        pedido.setUsuarioCancelacion(usuario);

        return aRespuesta(pedidoRepository.save(pedido));
    }

    private Pedido buscarPedido(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    private PedidoResponse aRespuesta(Pedido pedido) {
        List<PartidaPedidoResponse> partidas = pedido.getPartidas().stream()
                .map(d -> new PartidaPedidoResponse(
                        d.getMedicamento().getClave(),
                        d.getMedicamento().getNombreGenerico(),
                        d.getCantidadEsperada(),
                        d.getCantidadRecibida()))
                .toList();

        return new PedidoResponse(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getProveedor(),
                pedido.getEstatus(),
                pedido.getFecha(),
                partidas
        );
    }
}
