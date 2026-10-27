package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.EntregaPacienteResponse;
import com.example.backend.farmacia.dto.PasoRecorridoResponse;
import com.example.backend.farmacia.dto.TrazabilidadResponse;
import com.example.backend.farmacia.entity.CorteFarmacia;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.entity.PartidaRecepcionFarmacia;
import com.example.backend.farmacia.repository.CorteFarmaciaRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import com.example.backend.farmacia.repository.MovimientoFarmaciaRepository;
import com.example.backend.farmacia.repository.PartidaEntregaRepository;
import com.example.backend.farmacia.repository.PartidaRecepcionFarmaciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HU33: dado un lote, su recorrido del pedido a los pacientes que lo recibieron; dado un
 * expediente, los lotes que recibió el paciente. Solo Farmacia y Supervisión lo consultan
 * y cada consulta queda registrada con usuario, fecha y hora (CA4).
 */
@Service
public class ServicioTrazabilidad {

    private final LoteFarmaciaRepository loteFarmaciaRepository;
    private final LoteRepository loteRepository;
    private final PartidaRecepcionFarmaciaRepository recepcionRepository;
    private final PartidaEntregaRepository partidaEntregaRepository;
    private final MovimientoFarmaciaRepository movimientoFarmaciaRepository;
    private final CorteFarmaciaRepository corteRepository;
    private final ServicioInventarioFarmacia inventario;
    private final JdbcTemplate haero;

    public ServicioTrazabilidad(LoteFarmaciaRepository loteFarmaciaRepository,
                                LoteRepository loteRepository,
                                PartidaRecepcionFarmaciaRepository recepcionRepository,
                                PartidaEntregaRepository partidaEntregaRepository,
                                MovimientoFarmaciaRepository movimientoFarmaciaRepository,
                                CorteFarmaciaRepository corteRepository,
                                ServicioInventarioFarmacia inventario,
                                JdbcTemplate haero) {
        this.loteFarmaciaRepository = loteFarmaciaRepository;
        this.loteRepository = loteRepository;
        this.recepcionRepository = recepcionRepository;
        this.partidaEntregaRepository = partidaEntregaRepository;
        this.movimientoFarmaciaRepository = movimientoFarmaciaRepository;
        this.corteRepository = corteRepository;
        this.inventario = inventario;
        this.haero = haero;
    }

    @Transactional
    public TrazabilidadResponse porLote(String numeroLote, String clave, String nombreUsuario) {
        String lote = ServicioInventarioFarmacia.texto(numeroLote);
        if (lote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe o escanea el lote");
        }
        String filtroClave = ServicioInventarioFarmacia.texto(clave);
        registrarConsulta(nombreUsuario, "LOTE", lote);

        List<LoteFarmacia> enFarmacia = loteFarmaciaRepository.findByNumeroLoteIgnoreCase(lote).stream()
                .filter(l -> filtroClave == null || l.getMedicamento().getClave().equals(filtroClave)).toList();
        Map<Long, Lote> enAlmacen = new LinkedHashMap<>();
        loteRepository.findByNumeroLoteIgnoreCase(lote).stream()
                .filter(l -> filtroClave == null || l.getMedicamento().getClave().equals(filtroClave))
                .forEach(l -> enAlmacen.put(l.getId(), l));
        enFarmacia.stream().map(LoteFarmacia::getLoteAlmacen).filter(Objects::nonNull).forEach(l -> enAlmacen.putIfAbsent(l.getId(), l));
        if (enFarmacia.isEmpty() && enAlmacen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay registro del lote " + lote + " en HAERO");
        }

        List<Long> idsFarmacia = enFarmacia.stream().map(LoteFarmacia::getId).toList();
        List<PartidaEntrega> partidas = idsFarmacia.isEmpty() ? List.of() : partidaEntregaRepository.findByLoteIdIn(idsFarmacia);

        List<PasoRecorridoResponse> recorrido = new ArrayList<>();
        recorrido.add(pasoPedido(enAlmacen.values()));
        recorrido.add(pasoEntradaAlmacen(enAlmacen.values()));
        recorrido.add(pasoDespacho(enAlmacen.keySet()));
        recorrido.add(pasoRecepcionFarmacia(idsFarmacia));
        recorrido.add(pasoEntregas(partidas));
        recorrido.add(pasoBajaIntelisis(partidas));

        List<EntregaPacienteResponse> entregas = partidas.stream()
                .sorted(Comparator.comparing((PartidaEntrega p) -> p.getEntrega().getFecha()).reversed())
                .map(ServicioTrazabilidad::aRespuesta).toList();
        return new TrazabilidadResponse("LOTE", lote, recorrido, entregas);
    }

    @Transactional
    public TrazabilidadResponse porExpediente(String expediente, String nombreUsuario) {
        String exp = ServicioInventarioFarmacia.texto(expediente);
        if (exp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el número de expediente");
        }
        registrarConsulta(nombreUsuario, "EXPEDIENTE", exp);
        List<EntregaPacienteResponse> entregas = partidaEntregaRepository.findByEntregaRecetaPacienteExpedienteOrderByEntregaFechaDesc(exp)
                .stream().map(ServicioTrazabilidad::aRespuesta).toList();
        return new TrazabilidadResponse("EXPEDIENTE", exp, List.of(), entregas);
    }

    private void registrarConsulta(String nombreUsuario, String modo, String valor) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        haero.update("INSERT INTO farmacia_consulta_trazabilidad (usuario_id, modo, valor) VALUES (?, ?, ?)",
                usuario.getId(), modo, valor.length() > 60 ? valor.substring(0, 60) : valor);
    }

    private static PasoRecorridoResponse pasoPedido(java.util.Collection<Lote> lotes) {
        List<String> pedidos = new ArrayList<>();
        LocalDateTime fecha = null;
        for (Lote lote : lotes) {
            Pedido pedido = lote.getPedido();
            if (pedido == null) {
                continue;
            }
            String contrato = lote.getContrato() != null ? lote.getContrato() : pedido.getContrato();
            pedidos.add("Pedido " + pedido.getNumeroPedido() + (contrato != null ? " · contrato " + contrato : "") + " · " + pedido.getProveedor());
            LocalDateTime delPedido = pedido.getFecha().atStartOfDay();
            fecha = fecha == null || delPedido.isBefore(fecha) ? delPedido : fecha;
        }
        return new PasoRecorridoResponse("Pedido y contrato", fecha,
                pedidos.isEmpty() ? "Sin pedido registrado (alta inicial, donación o traspaso)" : String.join("; ", pedidos));
    }

    private static PasoRecorridoResponse pasoEntradaAlmacen(java.util.Collection<Lote> lotes) {
        LocalDateTime fecha = lotes.stream().map(Lote::getFechaAlta).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        String detalle = lotes.isEmpty() ? "El lote no pasó por Almacén en HAERO (se conoce por el alta inicial de Farmacia)"
                : lotes.stream().map(l -> l.getMedicamento().getClave() + " · caduca " + l.getCaducidad()
                        + (l.getMovimientoIntelisis() != null ? " · " + l.getMovimientoIntelisis() : "")
                        + (l.getCajasConfirmadas() != null ? " · " + l.getCajasConfirmadas() + " cajas" : ""))
                .collect(Collectors.joining("; "));
        return new PasoRecorridoResponse("Entrada a Almacén", fecha, detalle);
    }

    private PasoRecorridoResponse pasoDespacho(Set<Long> lotesAlmacen) {
        if (lotesAlmacen.isEmpty()) {
            return new PasoRecorridoResponse("Despacho a Farmacia", null, "Sin despachos de Almacén");
        }
        String marcadores = lotesAlmacen.stream().map(id -> "?").collect(Collectors.joining(", "));
        Map<String, Object> fila = haero.queryForMap(
                "SELECT MIN(m.fecha) AS primera, COUNT(*) AS despachos, COALESCE(SUM(m.cantidad_cajas), 0) AS cajas "
                        + "FROM movimiento m JOIN existencia e ON e.id = m.existencia_id "
                        + "WHERE m.tipo = 'SALIDA' AND m.solicitud_id IS NOT NULL AND e.lote_id IN (" + marcadores + ")",
                lotesAlmacen.toArray());
        LocalDateTime fecha = fila.get("primera") instanceof Timestamp t ? t.toLocalDateTime() : null;
        int despachos = ((Number) fila.get("despachos")).intValue();
        return new PasoRecorridoResponse("Despacho a Farmacia", fecha,
                despachos == 0 ? "Sin despachos de Almacén" : fila.get("cajas") + " cajas en " + despachos + (despachos == 1 ? " despacho" : " despachos"));
    }

    private PasoRecorridoResponse pasoRecepcionFarmacia(List<Long> lotesFarmacia) {
        if (lotesFarmacia.isEmpty()) {
            return new PasoRecorridoResponse("Recepción en Farmacia", null, "El lote no ha llegado a Farmacia");
        }
        List<PartidaRecepcionFarmacia> recibidas = recepcionRepository.findByLoteIdIn(lotesFarmacia);
        int altaInicial = movimientoFarmaciaRepository.findByLoteIdInOrderByFechaAsc(lotesFarmacia).stream()
                .filter(m -> MovimientoFarmacia.ALTA_INICIAL.equals(m.getTipo())).mapToInt(MovimientoFarmacia::getPiezas).sum();
        LocalDateTime fecha = recibidas.stream().map(p -> p.getRecepcion().getFecha()).min(Comparator.naturalOrder()).orElse(null);
        List<String> detalle = new ArrayList<>();
        if (!recibidas.isEmpty()) {
            detalle.add(recibidas.stream().mapToInt(PartidaRecepcionFarmacia::getCajasRecibidas).sum() + " cajas recibidas ("
                    + recibidas.stream().mapToInt(PartidaRecepcionFarmacia::getPiezasRecibidas).sum() + " piezas)");
        }
        if (altaInicial > 0) {
            detalle.add("Alta inicial de Farmacia: " + altaInicial + " piezas");
        }
        return new PasoRecorridoResponse("Recepción en Farmacia", fecha, detalle.isEmpty() ? "Sin recepciones" : String.join("; ", detalle));
    }

    private static PasoRecorridoResponse pasoEntregas(List<PartidaEntrega> partidas) {
        if (partidas.isEmpty()) {
            return new PasoRecorridoResponse("Entregas a pacientes", null, "Todavía no se entrega a ningún paciente");
        }
        LocalDateTime primera = partidas.stream().map(p -> p.getEntrega().getFecha()).min(Comparator.naturalOrder()).orElse(null);
        long pacientes = partidas.stream().map(p -> p.getEntrega().getReceta().getPacienteExpediente() != null
                ? p.getEntrega().getReceta().getPacienteExpediente() : p.getEntrega().getReceta().getPacienteNombre()).distinct().count();
        int piezas = partidas.stream().mapToInt(p -> p.getPiezas() - p.getPiezasDevueltas()).sum();
        return new PasoRecorridoResponse("Entregas a pacientes", primera,
                partidas.size() + (partidas.size() == 1 ? " entrega" : " entregas") + " a " + pacientes
                        + (pacientes == 1 ? " paciente" : " pacientes") + ", " + piezas + " piezas (ya restadas las devoluciones)");
    }

    private PasoRecorridoResponse pasoBajaIntelisis(List<PartidaEntrega> partidas) {
        Set<LocalDate> dias = partidas.stream().map(p -> p.getEntrega().getFecha().toLocalDate()).collect(Collectors.toSet());
        if (dias.isEmpty()) {
            return new PasoRecorridoResponse("Baja en Intelisis", null, "Sin entregas que dar de baja");
        }
        List<CorteFarmacia> cortes = corteRepository.findAllById(dias);
        LocalDateTime ultima = cortes.stream().map(CorteFarmacia::getMarcadoEn).max(Comparator.naturalOrder()).orElse(null);
        return new PasoRecorridoResponse("Baja en Intelisis", ultima,
                cortes.size() + " de " + dias.size() + (dias.size() == 1 ? " día dado de baja" : " días dados de baja"));
    }

    private static EntregaPacienteResponse aRespuesta(PartidaEntrega partida) {
        var entrega = partida.getEntrega();
        var receta = entrega.getReceta();
        return new EntregaPacienteResponse(entrega.getFecha(), entrega.folio(), receta.getPacienteNombre(), receta.getPacienteExpediente(),
                partida.getLote().getMedicamento().getClave(), partida.getLote().getNumeroLote(),
                partida.getPiezas() - partida.getPiezasDevueltas(), entrega.getUsuario().getNombreCompleto());
    }
}
