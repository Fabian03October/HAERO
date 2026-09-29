package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.AsociarCodigoRequest;
import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistrarEntradaResponse;
import com.example.backend.almacen.dto.ResolverCodigoResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.entity.PedidoDetalle;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.almacen.repository.PedidoDetalleRepository;
import com.example.backend.almacen.repository.PedidoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServicioRecepcion {

    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final CodigoBarrasRepository codigoBarrasRepository;
    private final LoteRepository loteRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicioRecepcion(PedidoRepository pedidoRepository,
                              PedidoDetalleRepository pedidoDetalleRepository,
                              MedicamentoRepository medicamentoRepository,
                              CodigoBarrasRepository codigoBarrasRepository,
                              LoteRepository loteRepository,
                              ExistenciaRepository existenciaRepository,
                              MovimientoRepository movimientoRepository,
                              UsuarioRepository usuarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoDetalleRepository = pedidoDetalleRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.codigoBarrasRepository = codigoBarrasRepository;
        this.loteRepository = loteRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public ResolverCodigoResponse resolverCodigo(String codigo) {
        CodigoBarras codigoBarras = codigoBarrasRepository.findById(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Código no registrado; asócialo a una clave y proveedor"));

        return new ResolverCodigoResponse(
                codigoBarras.getMedicamento().getClave(),
                codigoBarras.getMedicamento().getNombreGenerico(),
                codigoBarras.getProveedor()
        );
    }

    public void asociarCodigo(AsociarCodigoRequest request) {
        if (codigoBarrasRepository.existsById(request.getCodigo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese código ya está asociado a una clave");
        }

        Medicamento medicamento = medicamentoRepository.findById(request.getClave())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de medicamento no encontrada"));

        CodigoBarras codigoBarras = new CodigoBarras();
        codigoBarras.setCodigo(request.getCodigo());
        codigoBarras.setMedicamento(medicamento);
        codigoBarras.setProveedor(request.getProveedor());
        codigoBarrasRepository.save(codigoBarras);
    }

    @Transactional
    public RegistrarEntradaResponse registrarEntrada(RegistrarEntradaRequest request, String nombreUsuarioSolicitante) {
        if (request.getPedidoId() == null || request.getClave() == null || request.getNumeroLote() == null
                || request.getCaducidad() == null || request.getUbicaciones() == null || request.getUbicaciones().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan campos obligatorios (pedido, clave, lote, caducidad o cantidad)");
        }

        Medicamento medicamento = medicamentoRepository.findById(request.getClave())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de medicamento no encontrada"));

        Pedido pedido = pedidoRepository.findById(request.getPedidoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if ("CANCELADO".equals(pedido.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este pedido fue cancelado; no se puede recibir");
        }

        PedidoDetalle detalle = pedidoDetalleRepository
                .findByPedidoIdAndMedicamentoClave(pedido.getId(), request.getClave())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa clave no forma parte del pedido"));

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Lote lote = new Lote();
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(request.getNumeroLote());
        lote.setCaducidad(request.getCaducidad());
        lote.setProveedor(request.getProveedor());
        lote.setEstatus("DISPONIBLE");
        lote.setPedido(pedido);
        lote = loteRepository.save(lote);

        int totalCajas = 0;
        List<UbicacionCantidad> existenciasRespuesta = new ArrayList<>();

        for (UbicacionCantidad ubicacionCantidad : request.getUbicaciones()) {
            if (ubicacionCantidad.getCajas() == null || ubicacionCantidad.getCajas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de cajas debe ser mayor a cero");
            }

            Existencia existencia = new Existencia();
            existencia.setLote(lote);
            existencia.setUbicacion(ubicacionCantidad.getUbicacion());
            existencia.setCantidadCajas(ubicacionCantidad.getCajas());
            existencia = existenciaRepository.save(existencia);

            Movimiento movimiento = new Movimiento();
            movimiento.setExistencia(existencia);
            movimiento.setTipo("ENTRADA");
            movimiento.setCantidadCajas(ubicacionCantidad.getCajas());
            movimiento.setUsuario(usuario);
            movimientoRepository.save(movimiento);

            totalCajas += ubicacionCantidad.getCajas();
            existenciasRespuesta.add(ubicacionCantidad);
        }

        detalle.setCantidadRecibida(detalle.getCantidadRecibida() + totalCajas);
        pedidoDetalleRepository.save(detalle);

        return new RegistrarEntradaResponse(lote.getId(), lote.getNumeroLote(), lote.getCaducidad(), existenciasRespuesta);
    }
}
