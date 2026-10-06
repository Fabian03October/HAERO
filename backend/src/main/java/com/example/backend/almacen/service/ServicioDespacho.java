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

    public ServicioDespacho(MedicamentoRepository medicamentoRepository,
                             LoteRepository loteRepository,
                             ExistenciaRepository existenciaRepository,
                             MovimientoRepository movimientoRepository,
                             SolicitudRepository solicitudRepository,
                             UsuarioRepository usuarioRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.loteRepository = loteRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudRepository = solicitudRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ---------- HU12: existencias por ubicacion ----------

    public List<ExistenciaResponse> listarExistencias(String clave, String numeroLote, String ubicacion) {
        List<Existencia> existencias = existenciaRepository.findAll().stream()
                .filter(e -> clave == null || clave.isBlank() || e.getLote().getMedicamento().getClave().equalsIgnoreCase(clave))
                .filter(e -> numeroLote == null || numeroLote.isBlank() || e.getLote().getNumeroLote().equalsIgnoreCase(numeroLote))
                .filter(e -> ubicacion == null || ubicacion.isBlank() || e.getUbicacion().equalsIgnoreCase(ubicacion))
                .toList();

        Map<String, Long> loteFefoPorClave = new HashMap<>();
        for (Existencia e : existencias) {
            String claveMedicamento = e.getLote().getMedicamento().getClave();
            loteFefoPorClave.computeIfAbsent(claveMedicamento, this::idDelLoteFefo);
        }

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
                            e.getLote().getProveedor()
                    );
                })
                .toList();
    }

    private Long idDelLoteFefo(String claveMedicamento) {
        for (Lote lote : lotesDisponiblesOrdenadosPorCaducidad(claveMedicamento)) {
            int disponible = existenciaRepository.findByLoteId(lote.getId()).stream()
                    .mapToInt(Existencia::getCantidadCajas)
                    .sum();
            if (disponible > 0) {
                return lote.getId();
            }
        }
        return null;
    }

    // ---------- HU13: solicitud de Farmacia ----------

    public SolicitudResponse crearSolicitud(CrearSolicitudRequest request, String nombreUsuarioSolicitante) {
        if (request.getCantidad() == null || request.getCantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad solicitada debe ser mayor a cero");
        }

        Medicamento medicamento = medicamentoRepository.findById(request.getClave())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de medicamento no encontrada"));

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuarioSolicitante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Solicitud solicitud = new Solicitud();
        solicitud.setMedicamento(medicamento);
        solicitud.setCantidadSolicitada(request.getCantidad());
        solicitud.setCantidadAtendida(0);
        solicitud.setEstatus("PENDIENTE");
        solicitud.setUsuario(usuario);

        return aRespuesta(solicitudRepository.save(solicitud));
    }

    public List<SolicitudResponse> listarMisSolicitudes(String nombreUsuarioSolicitante) {
        return solicitudRepository.findByUsuarioNombreUsuario(nombreUsuarioSolicitante).stream()
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

            if (!request.isConfirmarFueraDeFefo()
                    && existeLoteConCaducidadMasProxima(clave, lote.getCaducidad(), restantePorExistencia, lote.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Existe un lote con caducidad más próxima");
            }

            restantePorExistencia.put(existencia.getId(), disponibleActual - partida.getCajas());
            totalDespachado += partida.getCajas();
        }

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
            movimientoRepository.save(movimiento);
        }

        solicitud.setCantidadAtendida(solicitud.getCantidadAtendida() + totalDespachado);
        solicitud.setEstatus(solicitud.getCantidadAtendida() >= solicitud.getCantidadSolicitada() ? "ATENDIDA" : "PARCIAL");
        solicitudRepository.save(solicitud);

        return new DespacharSolicitudResponse(
                solicitud.getId(), solicitud.getEstatus(), solicitud.getCantidadSolicitada(), solicitud.getCantidadAtendida());
    }

    // ---------- helpers ----------

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
                solicitud.getFecha()
        );
    }
}
