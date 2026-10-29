package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CrearSolicitudRequest;
import com.example.backend.almacen.dto.DespacharSolicitudRequest;
import com.example.backend.almacen.dto.DespacharSolicitudResponse;
import com.example.backend.almacen.dto.ExistenciaResponse;
import com.example.backend.almacen.dto.PartidaDespacho;
import com.example.backend.almacen.dto.SolicitudResponse;
import com.example.backend.almacen.dto.SugerenciaDespachoResponse;
import com.example.backend.almacen.dto.SugerenciaPartida;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.almacen.repository.SolicitudRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cubre HU12 (existencias por ubicacion), HU13 (solicitud de Farmacia),
 * HU14 (validacion y despacho) y HU15 (bloqueo FEFO), tal como lo agrupa
 * la carta CRC de ServicioDespacho en el documento de diseno.
 *
 * Ajuste HU15: el bloqueo FEFO (tomar primero el lote que caduca antes) se
 * puede saltar si el despachador lo confirma explicitamente en pantalla
 * (DespacharSolicitudRequest.confirmarFueraDeFefo). El bloqueo de lote YA
 * vencido nunca se puede saltar; eso no es una preferencia de orden, es
 * que no se dispensa medicamento caducado.
 */
@Service
public class ServicioDespacho {

    private final MedicamentoRepository medicamentoRepository;
    private final LoteRepository loteRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final SolicitudRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final CodigoBarrasRepository codigoBarrasRepository;
    private final com.example.backend.almacen.repository.SolicitudPedidoRepository solicitudPedidoRepository;

    public ServicioDespacho(MedicamentoRepository medicamentoRepository,
                             LoteRepository loteRepository,
                             ExistenciaRepository existenciaRepository,
                             MovimientoRepository movimientoRepository,
                             SolicitudRepository solicitudRepository,
                             UsuarioRepository usuarioRepository,
                             CodigoBarrasRepository codigoBarrasRepository,
                             com.example.backend.almacen.repository.SolicitudPedidoRepository solicitudPedidoRepository) {
        this.solicitudPedidoRepository = solicitudPedidoRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.loteRepository = loteRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
        this.codigoBarrasRepository = codigoBarrasRepository;
    }

    // ---------- HU12: existencias por ubicacion ----------

    public List<ExistenciaResponse> listarExistencias(String clave, String numeroLote, String ubicacion) {
        // Una sola lectura: con ella se calcula el lote FEFO de cada clave y se filtra.
        List<Existencia> todas = existenciaRepository.findAllConLote();
        Map<String, Long> loteFefoPorClave = lotesFefo(todas);

        List<Existencia> existencias = todas.stream()
                .filter(e -> clave == null || clave.isBlank() || e.getLote().getMedicamento().getClave().equalsIgnoreCase(clave))
                .filter(e -> numeroLote == null || numeroLote.isBlank() || e.getLote().getNumeroLote().equalsIgnoreCase(numeroLote))
                .filter(e -> ubicacion == null || ubicacion.isBlank() || e.getUbicacion().equalsIgnoreCase(ubicacion))
                .toList();

        return existencias.stream()
                .map(e -> {
                    String claveMedicamento = e.getLote().getMedicamento().getClave();
                    Long loteFefoId = loteFefoPorClave.get(claveMedicamento);
                    boolean esFefo = e.getLote().getId().equals(loteFefoId);
                    return new ExistenciaResponse(
                            e.getId(),
                            claveMedicamento,
                            e.getLote().getMedicamento().getNombreGenerico(),
                            e.getLote().getId(),
                            e.getLote().getNumeroLote(),
                            e.getLote().getCaducidad(),
                            e.getUbicacion(),
                            e.getCantidadCajas(),
                            esFefo,
                            e.getLote().getProveedor(),
                            e.getLote().getMovimientoIntelisis(),
                            e.getLote().getFechaAlta() == null ? null : e.getLote().getFechaAlta().toLocalDate()
                    );
                })
                .toList();
    }

    /**
     * El lote que debe salir primero de cada clave (HU15): el DISPONIBLE, no vencido y
     * con cajas, que caduca antes. Se calcula en memoria con las existencias ya leídas,
     * en lugar de volver a consultar la base por cada clave y cada lote.
     */
    private Map<String, Long> lotesFefo(List<Existencia> existencias) {
        LocalDate hoy = LocalDate.now();
        Map<Long, Integer> cajasPorLote = new HashMap<>();
        Map<Long, Lote> lotes = new HashMap<>();
        for (Existencia e : existencias) {
            Lote lote = e.getLote();
            if (!"DISPONIBLE".equals(lote.getEstatus()) || lote.getCaducidad().isBefore(hoy)) {
                continue;
            }
            cajasPorLote.merge(lote.getId(), e.getCantidadCajas(), Integer::sum);
            lotes.putIfAbsent(lote.getId(), lote);
        }
        Map<String, Long> fefo = new HashMap<>();
        lotes.values().stream()
                .filter(lote -> cajasPorLote.getOrDefault(lote.getId(), 0) > 0)
                .sorted(Comparator.comparing(Lote::getCaducidad).thenComparing(Lote::getId))
                .forEach(lote -> fefo.putIfAbsent(lote.getMedicamento().getClave(), lote.getId()));
        return fefo;
    }

    // ---------- HU13: solicitud de Farmacia ----------

    public SolicitudResponse crearSolicitud(CrearSolicitudRequest request, String nombreUsuarioSolicitante) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return aRespuesta(solicitudRepository.save(nuevaSolicitud(request, usuario, null)));
    }

    // ---------- HU39: pedido con varias claves ----------

    private static final int MAX_CLAVES_POR_PEDIDO = 60;
    private static final List<String> ABIERTAS = List.of("PENDIENTE", "PARCIAL");

    /** Todas las claves se guardan o ninguna: si una no se puede pedir, no queda medio pedido. */
    @Transactional
    public List<SolicitudResponse> crearPedido(com.example.backend.almacen.dto.CrearPedidoRequest request, String nombreUsuarioSolicitante) {
        List<CrearSolicitudRequest> partidas = request.getPartidas() == null ? List.of() : request.getPartidas();
        if (partidas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos una clave al pedido");
        }
        if (partidas.size() > MAX_CLAVES_POR_PEDIDO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un pedido puede tener hasta " + MAX_CLAVES_POR_PEDIDO + " claves");
        }
        java.util.Set<String> vistas = new java.util.HashSet<>();
        for (CrearSolicitudRequest partida : partidas) {
            String clave = partida.getClave() == null ? "" : partida.getClave().strip();
            if (!vistas.add(clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + clave + " está dos veces en el pedido; junta las cantidades");
            }
        }
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        // Primero se validan todas; si una no se puede pedir, no se guarda nada.
        List<Solicitud> solicitudes = partidas.stream().map(p -> nuevaSolicitud(p, usuario, null)).toList();
        com.example.backend.almacen.entity.SolicitudPedido pedido = new com.example.backend.almacen.entity.SolicitudPedido();
        pedido.setUsuario(usuario);
        String observaciones = request.getObservaciones() == null ? "" : request.getObservaciones().strip();
        pedido.setObservaciones(observaciones.isEmpty() ? null : recortar(observaciones, 300));
        pedido = solicitudPedidoRepository.save(pedido);
        List<SolicitudResponse> creadas = new ArrayList<>();
        for (Solicitud solicitud : solicitudes) {
            solicitud.setPedido(pedido);
            creadas.add(aRespuesta(solicitudRepository.save(solicitud)));
        }
        return creadas;
    }

    /**
     * Despacha todas las claves abiertas del pedido con la sugerencia FEFO de cada una, en una
     * sola transacción: si una falla, no se despacha ninguna. Con parcial, una clave sin
     * existencia se queda pendiente y las demás salen.
     */
    @Transactional
    public com.example.backend.almacen.dto.DespachoPedidoResponse despacharPedido(Long pedidoId, boolean parcial, String nombreUsuarioSolicitante) {
        List<Solicitud> abiertas = solicitudRepository.findByPedidoIdAndEstatusInOrderByIdAsc(pedidoId, ABIERTAS);
        if (abiertas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido no tiene claves pendientes de despachar");
        }
        List<DespacharSolicitudResponse> despachadas = new ArrayList<>();
        List<String> sinExistencia = new ArrayList<>();
        for (Solicitud solicitud : abiertas) {
            String clave = solicitud.getMedicamento().getClave();
            SugerenciaDespachoResponse sugerencia = sugerir(solicitud.getId());
            if (sugerencia.getPartidas().isEmpty()) {
                if (!parcial) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay existencia de " + clave
                            + "; despacha el pedido como entrega parcial o cierra esa clave como \"no se puede surtir\"");
                }
                sinExistencia.add(clave);
                continue;
            }
            DespacharSolicitudRequest despacho = new DespacharSolicitudRequest();
            despacho.setParcial(parcial);
            despacho.setPartidas(sugerencia.getPartidas().stream().map(s -> {
                PartidaDespacho partida = new PartidaDespacho();
                partida.setExistenciaId(s.getExistenciaId());
                partida.setCajas(s.getCajasSugeridas());
                return partida;
            }).toList());
            try {
                despachadas.add(despachar(solicitud.getId(), despacho, nombreUsuarioSolicitante));
            } catch (ResponseStatusException e) {
                throw new ResponseStatusException(e.getStatusCode(), clave + ": " + e.getReason());
            }
        }
        return new com.example.backend.almacen.dto.DespachoPedidoResponse(pedidoId, despachadas, sinExistencia);
    }

    /** HU39 + HU40: Farmacia cancela de una vez todo lo que sigue pendiente del pedido. */
    @Transactional
    public List<SolicitudResponse> cancelarPedido(Long pedidoId, String motivo, String nombreUsuario) {
        List<Solicitud> abiertas = solicitudRepository.findByPedidoIdAndEstatusInOrderByIdAsc(pedidoId, ABIERTAS);
        if (abiertas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El pedido no tiene claves pendientes");
        }
        return abiertas.stream().map(s -> cancelar(s.getId(), motivo, nombreUsuario)).toList();
    }

    /** Valida una clave y arma su solicitud (sin guardarla). */
    private Solicitud nuevaSolicitud(CrearSolicitudRequest request, Usuario usuario, com.example.backend.almacen.entity.SolicitudPedido pedido) {
        if (request.getCantidad() == null || request.getCantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    (request.getClave() == null ? "" : request.getClave() + ": ") + "La cantidad solicitada debe ser mayor a cero");
        }

        Medicamento medicamento = medicamentoRepository.findById(request.getClave())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + request.getClave() + " no está en el catálogo"));
        // Diseno aprobado: solo se piden claves dadas de alta en HAERO (con codigo de barras).
        if (!codigoBarrasRepository.existsByMedicamentoClave(medicamento.getClave())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + medicamento.getClave() + " todavía no está dada de alta en el Almacén");
        }

        Solicitud solicitud = new Solicitud();
        solicitud.setMedicamento(medicamento);
        solicitud.setCantidadSolicitada(request.getCantidad());
        solicitud.setCantidadAtendida(0);
        solicitud.setEstatus("PENDIENTE");
        solicitud.setUsuario(usuario);
        solicitud.setPedido(pedido);
        return solicitud;
    }

    /**
     * Solicitudes de todo el personal de Farmacia, las más recientes primero. Son del
     * área y no de cada persona: así quien entra a otro turno ve lo pendiente y no se
     * pide dos veces lo mismo. Cada una conserva quién la hizo (HU13 CA1).
     */
    public List<SolicitudResponse> listarSolicitudesDeFarmacia() {
        return solicitudRepository.findAllByOrderByFechaDesc().stream()
                .map(this::aRespuesta)
                .toList();
    }

    public List<SolicitudResponse> listarBandeja(List<String> estatuses) {
        return solicitudRepository.findByEstatusIn(estatuses).stream()
                .map(this::aRespuesta)
                .toList();
    }

    // ---------- HU14 / HU15: sugerencia FEFO y despacho ----------

    public SugerenciaDespachoResponse sugerir(Long solicitudId) {
        Solicitud solicitud = buscarSolicitud(solicitudId);
        String clave = solicitud.getMedicamento().getClave();
        int pendiente = solicitud.getCantidadSolicitada() - solicitud.getCantidadAtendida();

        List<SugerenciaPartida> partidas = new ArrayList<>();
        int restante = pendiente;

        for (Lote lote : lotesDisponiblesOrdenadosPorCaducidad(clave)) {
            if (restante <= 0) {
                break;
            }
            for (Existencia existencia : existenciaRepository.findByLoteId(lote.getId())) {
                if (restante <= 0) {
                    break;
                }
                if (existencia.getCantidadCajas() <= 0) {
                    continue;
                }
                int sugerido = Math.min(restante, existencia.getCantidadCajas());
                partidas.add(new SugerenciaPartida(
                        existencia.getId(), lote.getId(), lote.getNumeroLote(), lote.getCaducidad(),
                        existencia.getUbicacion(), existencia.getCantidadCajas(), sugerido));
                restante -= sugerido;
            }
        }

        return new SugerenciaDespachoResponse(solicitudId, pendiente, partidas);
    }

    @Transactional
    public DespacharSolicitudResponse despachar(Long solicitudId, DespacharSolicitudRequest request, String nombreUsuarioSolicitante) {
        Solicitud solicitud = buscarSolicitud(solicitudId);

        if ("ATENDIDA".equals(solicitud.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud ya fue atendida");
        }
        if ("CANCELADA".equals(solicitud.getEstatus()) || "RECHAZADA".equals(solicitud.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La solicitud ya está " + solicitud.getEstatus().toLowerCase() + ": " + solicitud.getMotivoCierre());
        }

        if (request.getPartidas() == null || request.getPartidas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar al menos una partida a despachar");
        }

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String clave = solicitud.getMedicamento().getClave();
        int pendiente = solicitud.getCantidadSolicitada() - solicitud.getCantidadAtendida();

        // Snapshot mutable: si una partida "consume" un lote FEFO, la siguiente
        // partida del mismo despacho ya ve ese remanente al validar el bloqueo.
        Map<Long, Integer> restantePorExistencia = new HashMap<>();
        Map<Long, Existencia> existenciasPorId = new HashMap<>();
        int totalDespachado = 0;
        boolean fueraDeFefo = false;

        for (PartidaDespacho partida : request.getPartidas()) {
            Existencia existencia = existenciaRepository.findById(partida.getExistenciaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Existencia no encontrada"));
            existenciasPorId.putIfAbsent(existencia.getId(), existencia);

            Lote lote = existencia.getLote();
            if (!lote.getMedicamento().getClave().equals(clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa existencia no corresponde a la clave solicitada");
            }

            if (lote.getCaducidad().isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El lote " + lote.getNumeroLote() + " está vencido y no puede despacharse");
            }

            int disponibleActual = restantePorExistencia.computeIfAbsent(existencia.getId(), id -> existencia.getCantidadCajas());

            if (partida.getCajas() == null || partida.getCajas() <= 0 || partida.getCajas() > disponibleActual) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad excede la existencia disponible en esa ubicación");
            }

            if (existeLoteConCaducidadMasProxima(clave, lote.getCaducidad(), restantePorExistencia, lote.getId())) {
                if (!request.isConfirmarFueraDeFefo()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Existe un lote con caducidad más próxima");
                }
                fueraDeFefo = true;
            }

            restantePorExistencia.put(existencia.getId(), disponibleActual - partida.getCajas());
            totalDespachado += partida.getCajas();
        }

        String motivo = request.getMotivoFueraDeFefo() == null ? "" : request.getMotivoFueraDeFefo().strip();
        if (fueraDeFefo && motivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe por qué despachas fuera del orden FEFO");
        }
        String motivoMovimiento = fueraDeFefo ? recortar("Fuera de FEFO: " + motivo, 300) : null;

        if (totalDespachado > pendiente) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad despachada excede lo pendiente de la solicitud");
        }

        if (totalDespachado < pendiente && !request.isParcial()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad no cubre lo solicitado; agregue otra ubicación/lote o confirme como entrega parcial");
        }

        for (PartidaDespacho partida : request.getPartidas()) {
            Existencia existencia = existenciasPorId.get(partida.getExistenciaId());
            existencia.setCantidadCajas(existencia.getCantidadCajas() - partida.getCajas());
            existenciaRepository.save(existencia);

            Movimiento movimiento = new Movimiento();
            movimiento.setExistencia(existencia);
            movimiento.setTipo("SALIDA");
            movimiento.setCantidadCajas(partida.getCajas());
            movimiento.setUsuario(usuario);
            movimiento.setSolicitud(solicitud);
            movimiento.setMotivo(motivoMovimiento);
            movimientoRepository.save(movimiento);
        }

        solicitud.setCantidadAtendida(solicitud.getCantidadAtendida() + totalDespachado);
        solicitud.setEstatus(solicitud.getCantidadAtendida() >= solicitud.getCantidadSolicitada() ? "ATENDIDA" : "PARCIAL");
        solicitudRepository.save(solicitud);

        return new DespacharSolicitudResponse(
                solicitud.getId(), solicitud.getEstatus(), solicitud.getCantidadSolicitada(), solicitud.getCantidadAtendida());
    }

    // ---------- HU40: cancelar (Farmacia) y rechazar (Almacén) ----------

    public SolicitudResponse cancelar(Long solicitudId, String motivo, String nombreUsuario) {
        String texto = motivo == null || motivo.isBlank() ? "Farmacia ya no lo necesita" : motivo.strip();
        return cerrar(solicitudId, "CANCELADA", texto, nombreUsuario);
    }

    public SolicitudResponse rechazar(Long solicitudId, String motivo, String nombreUsuario) {
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe por qué no se puede surtir (por ejemplo, sin existencia)");
        }
        return cerrar(solicitudId, "RECHAZADA", motivo.strip(), nombreUsuario);
    }

    /** Solo lo pendiente: lo ya despachado (cantidadAtendida) se conserva. */
    private SolicitudResponse cerrar(Long solicitudId, String estatus, String motivo, String nombreUsuario) {
        Solicitud solicitud = buscarSolicitud(solicitudId);
        if (!"PENDIENTE".equals(solicitud.getEstatus()) && !"PARCIAL".equals(solicitud.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede cerrar una solicitud pendiente o parcial; esta ya está " + solicitud.getEstatus().toLowerCase());
        }
        solicitud.setEstatus(estatus);
        solicitud.setMotivoCierre(recortar(motivo, 300));
        solicitud.setCerradaPor(usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
        solicitud.setFechaCierre(java.time.LocalDateTime.now());
        return aRespuesta(solicitudRepository.save(solicitud));
    }

    // ---------- helpers ----------

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private Solicitud buscarSolicitud(Long id) {
        return solicitudRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
    }

    private List<Lote> lotesDisponiblesOrdenadosPorCaducidad(String claveMedicamento) {
        LocalDate hoy = LocalDate.now();
        return loteRepository.findByMedicamentoClaveAndEstatus(claveMedicamento, "DISPONIBLE").stream()
                .filter(lote -> !lote.getCaducidad().isBefore(hoy)) // excluye vencidos aunque no esten en "caducados" (ajuste HU15)
                .sorted(Comparator.comparing(Lote::getCaducidad))
                .toList();
    }

    /**
     * HU15 CA1/CA2: existe otro lote de la misma clave con caducidad
     * ESTRICTAMENTE mas proxima que la del lote elegido y con existencia
     * disponible en alguna ubicacion (considerando lo ya "consumido" en este
     * mismo despacho). Los lotes con la MISMA caducidad no bloquean (CA3).
     */
    private boolean existeLoteConCaducidadMasProxima(String claveMedicamento, LocalDate caducidadElegida,
                                                      Map<Long, Integer> restantePorExistencia, Long loteElegidoId) {
        LocalDate hoy = LocalDate.now();
        for (Lote lote : loteRepository.findByMedicamentoClaveAndEstatus(claveMedicamento, "DISPONIBLE")) {
            if (lote.getId().equals(loteElegidoId)) {
                continue;
            }
            if (lote.getCaducidad().isBefore(hoy)) {
                continue;
            }
            if (!lote.getCaducidad().isBefore(caducidadElegida)) {
                continue;
            }
            int disponible = existenciaRepository.findByLoteId(lote.getId()).stream()
                    .mapToInt(e -> restantePorExistencia.getOrDefault(e.getId(), e.getCantidadCajas()))
                    .sum();
            if (disponible > 0) {
                return true;
            }
        }
        return false;
    }

    private SolicitudResponse aRespuesta(Solicitud solicitud) {
        return new SolicitudResponse(
                solicitud.getId(),
                solicitud.getMedicamento().getClave(),
                solicitud.getMedicamento().getNombreGenerico(),
                solicitud.getCantidadSolicitada(),
                solicitud.getCantidadAtendida(),
                solicitud.getEstatus(),
                solicitud.getFecha(),
                solicitud.getUsuario() != null ? solicitud.getUsuario().getNombreCompleto() : null,
                solicitud.getUsuario() != null ? solicitud.getUsuario().getNombreUsuario() : null,
                solicitud.getMotivoCierre(),
                solicitud.getCerradaPor() != null ? solicitud.getCerradaPor().getNombreCompleto() : null,
                solicitud.getFechaCierre(),
                solicitud.getPedido() != null ? solicitud.getPedido().getId() : null
        );
    }
}
