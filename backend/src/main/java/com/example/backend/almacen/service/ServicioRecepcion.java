package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.AsociarCodigoRequest;
import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistrarEntradaResponse;
import com.example.backend.almacen.dto.RegistroIntelisisResponse;
import com.example.backend.almacen.dto.ResolverCodigoResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Pedido;
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
    private final ServicioRegistrosIntelisis servicioRegistrosIntelisis;

    public ServicioRecepcion(PedidoRepository pedidoRepository,
                              PedidoDetalleRepository pedidoDetalleRepository,
                              MedicamentoRepository medicamentoRepository,
                              CodigoBarrasRepository codigoBarrasRepository,
                              LoteRepository loteRepository,
                              ExistenciaRepository existenciaRepository,
                              MovimientoRepository movimientoRepository,
                              UsuarioRepository usuarioRepository,
                              ServicioRegistrosIntelisis servicioRegistrosIntelisis) {
        this.servicioRegistrosIntelisis = servicioRegistrosIntelisis;
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

    /** Registros de Intelisis que se pueden elegir para una clave (ENTRADA o ALTA_INICIAL). */
    public List<RegistroIntelisisResponse> registrosIntelisis(String clave, String tipo) {
        return servicioRegistrosIntelisis.listar(clave, tipo);
    }

    /**
     * Entrada (HU10) o alta inicial (HU21), según el diseño aprobado: se escanea la caja,
     * se elige el registro de Intelisis y Almacén solo pone la ubicación y las cajas que
     * contó. Lote, caducidad, proveedor, pedido y contrato se toman del registro de
     * Intelisis y quedan como "foto" en el lote. Sin registro en Intelisis no se recibe.
     */
    @Transactional
    public RegistrarEntradaResponse registrarEntrada(RegistrarEntradaRequest request, String nombreUsuarioSolicitante) {
        String codigo = recortar(request.getCodigo());
        String clave = recortar(request.getClave());
        if (codigo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escanea el código de barras de la caja");
        }
        if (clave == null || request.getOrigen() == null || recortar(request.getLote()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el registro de Intelisis que estás recibiendo");
        }
        List<UbicacionCantidad> ubicaciones = request.getUbicaciones() == null ? List.of() : request.getUbicaciones();
        if (ubicaciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la ubicación y las cajas");
        }
        for (UbicacionCantidad ubicacion : ubicaciones) {
            if (recortar(ubicacion.getUbicacion()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la ubicación de cada grupo de cajas");
            }
            if (ubicacion.getCajas() == null || ubicacion.getCajas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de cajas debe ser mayor a cero");
            }
        }

        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La clave " + clave + " no está en el catálogo de Intelisis; no se puede registrar"));

        RegistroIntelisisResponse registro = servicioRegistrosIntelisis
                .buscar(request.getOrigen(), request.getIdIntelisis(), clave, request.getLote())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ese registro no está en Intelisis (o ya no está vigente). Hasta que Intelisis lo tenga capturado no se puede recibir."));
        if (registro.bloqueo() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, registro.bloqueo());
        }

        // El código de barras es lo que da de alta la clave en HAERO.
        CodigoBarras codigoBarras = codigoBarrasRepository.findById(codigo).orElse(null);
        if (codigoBarras != null && !codigoBarras.getMedicamento().getClave().equals(clave)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ese código ya está asociado a la clave " + codigoBarras.getMedicamento().getClave());
        }
        if (codigoBarras == null) {
            codigoBarras = new CodigoBarras();
            codigoBarras.setCodigo(codigo);
            codigoBarras.setMedicamento(medicamento);
            codigoBarras.setProveedor(recortar(proveedorDe(registro), 120));
            codigoBarrasRepository.save(codigoBarras);
        }

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Pedido pedido = registro.pedidoId() == null ? null : pedidoRepository.findById(registro.pedidoId()).orElse(null);
        int totalCajas = ubicaciones.stream().mapToInt(UbicacionCantidad::getCajas).sum();

        Lote lote = new Lote();
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(recortar(registro.lote(), 40));
        lote.setCaducidad(registro.caducidad());
        lote.setProveedor(recortar(proveedorDe(registro), 120));
        lote.setEstatus("DISPONIBLE");
        lote.setPedido(pedido);
        lote.setOrigenIntelisis(registro.origen());
        lote.setIdIntelisis(registro.idIntelisis());
        lote.setMovimientoIntelisis(recortar(registro.movimiento(), 60));
        lote.setReferenciaIntelisis(recortar(registro.referencia(), 100));
        lote.setLoteIntelisis(recortar(registro.lote(), 50));
        lote.setFechaIntelisis(registro.fecha());
        lote.setPiezasIntelisis(registro.piezas());
        lote.setCajasIntelisis(registro.cajas());
        lote.setCajasConfirmadas(totalCajas);
        lote.setObservaciones(recortar(request.getObservaciones(), 300));
        lote.setContrato(recortar(registro.contrato(), 60));
        lote = loteRepository.save(lote);

        // El alta inicial no es una entrada nueva: es lo que ya estaba en los anaqueles.
        String tipoMovimiento = "EXISTENCIA".equals(registro.origen()) ? "CARGA_INICIAL" : "ENTRADA";
        List<UbicacionCantidad> existenciasRespuesta = new ArrayList<>();
        for (UbicacionCantidad ubicacionCantidad : ubicaciones) {
            Existencia existencia = new Existencia();
            existencia.setLote(lote);
            existencia.setUbicacion(recortar(ubicacionCantidad.getUbicacion(), 40));
            existencia.setCantidadCajas(ubicacionCantidad.getCajas());
            existencia = existenciaRepository.save(existencia);

            Movimiento movimiento = new Movimiento();
            movimiento.setExistencia(existencia);
            movimiento.setTipo(tipoMovimiento);
            movimiento.setCantidadCajas(ubicacionCantidad.getCajas());
            movimiento.setUsuario(usuario);
            movimientoRepository.save(movimiento);

            existenciasRespuesta.add(ubicacionCantidad);
        }

        // Lo recibido físicamente cuenta en la partida del pedido (lo de Intelisis no se toca).
        if (pedido != null) {
            pedidoDetalleRepository.findByPedidoIdAndMedicamentoClave(pedido.getId(), clave).ifPresent(detalle -> {
                detalle.setCantidadRecibida(detalle.getCantidadRecibida() + totalCajas);
                pedidoDetalleRepository.save(detalle);
            });
        }

        return new RegistrarEntradaResponse(lote.getId(), lote.getNumeroLote(), lote.getCaducidad(), existenciasRespuesta,
                lote.getMovimientoIntelisis(), registro.cajas(), totalCajas);
    }

    private static String proveedorDe(RegistroIntelisisResponse registro) {
        return registro.proveedor() != null ? registro.proveedor() : "Sin proveedor en Intelisis";
    }

    private static String recortar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }

    private static String recortar(String valor, int maximo) {
        String limpio = recortar(valor);
        return limpio == null || limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
