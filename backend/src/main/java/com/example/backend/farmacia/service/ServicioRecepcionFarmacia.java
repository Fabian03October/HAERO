package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.almacen.repository.SolicitudRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.DespachoPorRecibirResponse;
import com.example.backend.farmacia.dto.PartidaPorRecibir;
import com.example.backend.farmacia.dto.PartidaRecibida;
import com.example.backend.farmacia.dto.RecepcionResponse;
import com.example.backend.farmacia.dto.RecibirDespachoRequest;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaRecepcionFarmacia;
import com.example.backend.farmacia.entity.RecepcionFarmacia;
import com.example.backend.farmacia.repository.PartidaRecepcionFarmaciaRepository;
import com.example.backend.farmacia.repository.RecepcionFarmaciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HU26: Farmacia confirma lo que realmente llegó de cada despacho de Almacén. Lo recibido
 * entra al inventario de Farmacia con el mismo lote y caducidad, sin teclearlos. Pasar
 * medicamento de Almacén a Farmacia no es una baja: en Intelisis los dos son el almacén 25301.
 */
@Service
public class ServicioRecepcionFarmacia {

    private final PartidaRecepcionFarmaciaRepository partidaRepository;
    private final RecepcionFarmaciaRepository recepcionRepository;
    private final MovimientoRepository movimientoRepository;
    private final SolicitudRepository solicitudRepository;
    private final ServicioInventarioFarmacia inventario;

    public ServicioRecepcionFarmacia(PartidaRecepcionFarmaciaRepository partidaRepository,
                                     RecepcionFarmaciaRepository recepcionRepository,
                                     MovimientoRepository movimientoRepository,
                                     SolicitudRepository solicitudRepository,
                                     ServicioInventarioFarmacia inventario) {
        this.partidaRepository = partidaRepository;
        this.recepcionRepository = recepcionRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudRepository = solicitudRepository;
        this.inventario = inventario;
    }

    /** CA1: los despachos de Almacén por confirmar, agrupados por solicitud y con sus lotes. */
    public List<DespachoPorRecibirResponse> porRecibir() {
        Map<Long, List<Movimiento>> porSolicitud = new LinkedHashMap<>();
        for (Movimiento movimiento : partidaRepository.despachosPorRecibir()) {
            porSolicitud.computeIfAbsent(movimiento.getSolicitud().getId(), id -> new java.util.ArrayList<>()).add(movimiento);
        }
        return porSolicitud.values().stream().map(movimientos -> {
            Solicitud solicitud = movimientos.get(0).getSolicitud();
            Medicamento medicamento = solicitud.getMedicamento();
            List<PartidaPorRecibir> partidas = movimientos.stream().map(m -> {
                Lote lote = m.getExistencia().getLote();
                return new PartidaPorRecibir(m.getId(), lote.getNumeroLote(), lote.getCaducidad(), m.getCantidadCajas(),
                        m.getFecha(), m.getUsuario().getNombreCompleto());
            }).toList();
            int despachadas = movimientos.stream().mapToInt(Movimiento::getCantidadCajas).sum();
            return new DespachoPorRecibirResponse(solicitud.getId(), solicitud.getFecha(), medicamento.getClave(),
                    medicamento.getNombreGenerico(), medicamento.getPiezasPorCaja(), solicitud.getCantidadSolicitada(),
                    despachadas, partidas);
        }).toList();
    }

    @Transactional
    public RecepcionResponse recibir(RecibirDespachoRequest request, String nombreUsuario) {
        Solicitud solicitud = solicitudRepository.findById(request.getSolicitudId() == null ? -1 : request.getSolicitudId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (request.getPartidas() == null || request.getPartidas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las cajas que llegaron de cada lote");
        }
        Medicamento medicamento = solicitud.getMedicamento();
        Integer piezasPorCaja = medicamento.getPiezasPorCaja();
        if (piezasPorCaja == null || piezasPorCaja <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + medicamento.getClave()
                    + " no tiene piezas por caja en el catálogo; sin ese dato no se pueden pasar las cajas a piezas");
        }

        List<Long> ids = request.getPartidas().stream().map(PartidaRecibida::getMovimientoId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un lote del despacho viene repetido");
        }
        // CA4: un despacho solo se recibe una vez.
        Set<Long> yaRecibidos = partidaRepository.movimientosYaRecibidos(ids);
        if (!yaRecibidos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parte de este despacho ya se recibió en Farmacia; vuelve a cargar la lista");
        }

        Usuario usuario = inventario.usuario(nombreUsuario);
        RecepcionFarmacia recepcion = new RecepcionFarmacia();
        recepcion.setSolicitud(solicitud);
        recepcion.setUsuario(usuario);

        int despachadas = 0;
        int recibidas = 0;
        for (PartidaRecibida recibida : request.getPartidas()) {
            Movimiento movimiento = movimientoRepository.findById(recibida.getMovimientoId() == null ? -1 : recibida.getMovimientoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despacho no encontrado"));
            if (!"SALIDA".equals(movimiento.getTipo()) || movimiento.getSolicitud() == null
                    || !movimiento.getSolicitud().getId().equals(solicitud.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese despacho no corresponde a la solicitud");
            }
            if (recibida.getCajasRecibidas() == null || recibida.getCajasRecibidas() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las cajas que llegaron no pueden ser negativas");
            }
            Lote loteAlmacen = movimiento.getExistencia().getLote();
            // CA3: el mismo lote y caducidad del despacho; nada se teclea.
            LoteFarmacia lote = inventario.loteParaEntrada(medicamento, loteAlmacen.getNumeroLote(), loteAlmacen.getCaducidad(), loteAlmacen);

            PartidaRecepcionFarmacia partida = new PartidaRecepcionFarmacia();
            partida.setRecepcion(recepcion);
            partida.setMovimiento(movimiento);
            partida.setLote(lote);
            partida.setCajasDespachadas(movimiento.getCantidadCajas());
            partida.setCajasRecibidas(recibida.getCajasRecibidas());
            partida.setPiezasRecibidas(recibida.getCajasRecibidas() * piezasPorCaja);
            recepcion.getPartidas().add(partida);

            despachadas += movimiento.getCantidadCajas();
            recibidas += recibida.getCajasRecibidas();
        }

        // CA2: si llegó algo distinto, se registra la diferencia con su motivo y Almacén la ve.
        String motivo = ServicioInventarioFarmacia.texto(request.getMotivo());
        boolean conDiferencia = recepcion.getPartidas().stream().anyMatch(p -> !p.getCajasRecibidas().equals(p.getCajasDespachadas()));
        if (conDiferencia && motivo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lo que llegó no coincide con lo despachado: escribe el motivo de la diferencia");
        }
        recepcion.setMotivoDiferencia(conDiferencia ? motivo : null);
        recepcionRepository.save(recepcion);

        for (PartidaRecepcionFarmacia partida : recepcion.getPartidas()) {
            if (partida.getPiezasRecibidas() > 0) {
                inventario.mover(partida.getLote(), MovimientoFarmacia.RECEPCION, partida.getPiezasRecibidas(), partida.getId(), usuario);
            }
        }
        return aRespuesta(recepcion, despachadas, recibidas);
    }

    /** Recepciones registradas, las más recientes primero; con soloDiferencias, las que Almacén debe revisar. */
    public List<RecepcionResponse> recepciones(boolean soloDiferencias) {
        return recepcionRepository.findAllByOrderByFechaDesc().stream()
                .map(r -> aRespuesta(r,
                        r.getPartidas().stream().mapToInt(PartidaRecepcionFarmacia::getCajasDespachadas).sum(),
                        r.getPartidas().stream().mapToInt(PartidaRecepcionFarmacia::getCajasRecibidas).sum()))
                .filter(r -> !soloDiferencias || r.getDiferencia() != 0 || r.getMotivo() != null)
                .toList();
    }

    private static RecepcionResponse aRespuesta(RecepcionFarmacia recepcion, int despachadas, int recibidas) {
        Medicamento medicamento = recepcion.getSolicitud().getMedicamento();
        int piezas = recepcion.getPartidas().stream().mapToInt(PartidaRecepcionFarmacia::getPiezasRecibidas).sum();
        return new RecepcionResponse(recepcion.getId(), recepcion.getSolicitud().getId(), recepcion.getFecha(),
                recepcion.getUsuario().getNombreCompleto(), medicamento.getClave(), medicamento.getNombreGenerico(),
                despachadas, recibidas, piezas, recibidas - despachadas, recepcion.getMotivoDiferencia());
    }
}
