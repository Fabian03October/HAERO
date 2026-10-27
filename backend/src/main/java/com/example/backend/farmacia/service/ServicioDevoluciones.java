package com.example.backend.farmacia.service;

import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.DevolucionResponse;
import com.example.backend.farmacia.dto.EntregaEncontradaResponse;
import com.example.backend.farmacia.dto.PartidaDevueltaRequest;
import com.example.backend.farmacia.dto.RegistrarDevolucionRequest;
import com.example.backend.farmacia.entity.BajaFarmacia;
import com.example.backend.farmacia.entity.Devolucion;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaDevolucion;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.repository.CorteFarmaciaRepository;
import com.example.backend.farmacia.repository.DevolucionRepository;
import com.example.backend.farmacia.repository.EntregaRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import com.example.backend.farmacia.repository.PartidaEntregaRepository;
import com.example.backend.farmacia.repository.RecetaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HU34: medicamento que regresa un paciente o servicio. Se liga a su entrega; lo que
 * regresa en buen estado vuelve al mismo lote de Farmacia y lo dañado se registra como
 * merma (HU35). Se refleja en el resumen del día para la baja en Intelisis (HU31).
 */
@Service
public class ServicioDevoluciones {

    private static final Pattern FOLIO_ENTREGA = Pattern.compile("^(?:E-?)?0*(\\d{1,12})$", Pattern.CASE_INSENSITIVE);
    private static final int MAXIMO_ENTREGAS = 20;

    private final EntregaRepository entregaRepository;
    private final RecetaRepository recetaRepository;
    private final PartidaEntregaRepository partidaEntregaRepository;
    private final LoteFarmaciaRepository loteFarmaciaRepository;
    private final DevolucionRepository devolucionRepository;
    private final CorteFarmaciaRepository corteRepository;
    private final ServicioInventarioFarmacia inventario;

    public ServicioDevoluciones(EntregaRepository entregaRepository,
                                RecetaRepository recetaRepository,
                                PartidaEntregaRepository partidaEntregaRepository,
                                LoteFarmaciaRepository loteFarmaciaRepository,
                                DevolucionRepository devolucionRepository,
                                CorteFarmaciaRepository corteRepository,
                                ServicioInventarioFarmacia inventario) {
        this.entregaRepository = entregaRepository;
        this.recetaRepository = recetaRepository;
        this.partidaEntregaRepository = partidaEntregaRepository;
        this.loteFarmaciaRepository = loteFarmaciaRepository;
        this.devolucionRepository = devolucionRepository;
        this.corteRepository = corteRepository;
        this.inventario = inventario;
    }

    /** CA1: busca la entrega por su folio (E-000123), el folio de la receta, el expediente o el lote. */
    public List<EntregaEncontradaResponse> buscar(String texto) {
        String busqueda = ServicioInventarioFarmacia.texto(texto);
        if (busqueda == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el folio de la entrega, el expediente o el lote");
        }
        Map<Long, Entrega> encontradas = new LinkedHashMap<>();
        Matcher folio = FOLIO_ENTREGA.matcher(busqueda);
        if (folio.matches()) {
            entregaRepository.findById(Long.parseLong(folio.group(1))).ifPresent(e -> encontradas.put(e.getId(), e));
        }
        recetaRepository.findByFolioIgnoreCase(busqueda)
                .ifPresent(r -> entregaRepository.findByRecetaIdOrderByFechaAsc(r.getId()).forEach(e -> encontradas.put(e.getId(), e)));
        entregaRepository.findTop20ByRecetaPacienteExpedienteOrderByFechaDesc(busqueda).forEach(e -> encontradas.put(e.getId(), e));
        List<Long> lotes = loteFarmaciaRepository.findByNumeroLoteIgnoreCase(busqueda).stream().map(l -> l.getId()).toList();
        if (!lotes.isEmpty()) {
            partidaEntregaRepository.findByLoteIdIn(lotes).stream()
                    .map(PartidaEntrega::getEntrega)
                    .sorted(Comparator.comparing(Entrega::getFecha).reversed())
                    .forEach(e -> encontradas.putIfAbsent(e.getId(), e));
        }
        return encontradas.values().stream()
                .sorted(Comparator.comparing(Entrega::getFecha).reversed())
                .limit(MAXIMO_ENTREGAS)
                .map(ServicioDevoluciones::aRespuesta)
                .toList();
    }

    @Transactional
    public List<DevolucionResponse> registrar(RegistrarDevolucionRequest request, String nombreUsuario) {
        Entrega entrega = entregaRepository.findById(request.getEntregaId() == null ? -1 : request.getEntregaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega no encontrada"));
        String motivo = ServicioInventarioFarmacia.texto(request.getMotivo());
        if (motivo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo de la devolución");
        }
        List<PartidaDevueltaRequest> devueltas = request.getPartidas() == null ? List.of()
                : request.getPartidas().stream().filter(p -> p.getPiezas() != null && p.getPiezas() > 0).toList();
        if (devueltas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las piezas que se devuelven de al menos un lote");
        }
        Usuario usuario = inventario.usuario(nombreUsuario);

        // Si el día de la entrega ya se dio de baja en Intelisis, la devolución no puede restarse
        // ahí: aparece como "Devolucion Farmacia" en el resumen de hoy, para capturarla así.
        LocalDate diaEntrega = entrega.getFecha().toLocalDate();
        boolean diaYaDadoDeBaja = corteRepository.findById(diaEntrega).isPresent();
        Devolucion devolucion = new Devolucion();
        devolucion.setEntrega(entrega);
        devolucion.setMotivo(motivo);
        devolucion.setUsuario(usuario);
        devolucion.setEnResumen(diaYaDadoDeBaja ? Devolucion.DEVOLUCION : Devolucion.RESTA);
        devolucion.setDiaResumen(diaYaDadoDeBaja ? LocalDate.now() : diaEntrega);

        Set<Long> vistas = new HashSet<>();
        for (PartidaDevueltaRequest devuelta : devueltas) {
            PartidaEntrega partida = entrega.getPartidas().stream()
                    .filter(p -> p.getId().equals(devuelta.getPartidaId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese lote no es de la entrega " + entrega.folio()));
            if (!vistas.add(partida.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un lote de la entrega viene repetido");
            }
            // CA2: no se devuelve más de lo que se entregó de cada lote.
            int puede = partida.getPiezas() - partida.getPiezasDevueltas();
            if (devuelta.getPiezas() > puede) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Del lote " + partida.getLote().getNumeroLote()
                        + " solo se pueden devolver " + puede + " piezas");
            }
            partida.setPiezasDevueltas(partida.getPiezasDevueltas() + devuelta.getPiezas());
            PartidaDevolucion partidaDevolucion = new PartidaDevolucion();
            partidaDevolucion.setDevolucion(devolucion);
            partidaDevolucion.setPartidaEntrega(partida);
            partidaDevolucion.setPiezas(devuelta.getPiezas());
            partidaDevolucion.setDanado(devuelta.isDanado());
            devolucion.getPartidas().add(partidaDevolucion);
        }
        devolucionRepository.save(devolucion);

        for (PartidaDevolucion partida : devolucion.getPartidas()) {
            var lote = partida.getPartidaEntrega().getLote();
            if (partida.isDanado()) {
                // CA3: lo dañado no regresa al inventario; queda como merma.
                inventario.guardarBaja(lote, partida.getPiezas(), BajaFarmacia.DANO,
                        "Devolución dañada de la entrega " + entrega.folio() + ": " + motivo, partida, usuario);
            } else {
                inventario.mover(lote, MovimientoFarmacia.DEVOLUCION, partida.getPiezas(), partida.getId(), usuario);
            }
        }
        return devolucion.getPartidas().stream().map(p -> aRespuesta(devolucion, p)).toList();
    }

    /** Devoluciones registradas en el día (resumen de la pantalla). */
    public List<DevolucionResponse> delDia(LocalDate dia) {
        return devolucionRepository.findByFechaBetweenOrderByFechaAsc(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()).stream()
                .flatMap(d -> d.getPartidas().stream().map(p -> aRespuesta(d, p)))
                .toList();
    }

    private static DevolucionResponse aRespuesta(Devolucion devolucion, PartidaDevolucion partida) {
        var lote = partida.getPartidaEntrega().getLote();
        return new DevolucionResponse(partida.getId(), devolucion.getEntrega().folio(), lote.getMedicamento().getClave(),
                lote.getNumeroLote(), partida.getPiezas(), partida.isDanado(), devolucion.getFecha(),
                devolucion.getEnResumen(), devolucion.getDiaResumen());
    }

    static EntregaEncontradaResponse aRespuesta(Entrega entrega) {
        var receta = entrega.getReceta();
        return new EntregaEncontradaResponse(entrega.getId(), entrega.folio(), receta.getFolio(), receta.getPacienteExpediente(),
                receta.getPacienteNombre(), entrega.getFecha(), entrega.getUsuario().getNombreCompleto(),
                entrega.getPartidas().stream().map(ServicioEntregas::aRespuesta).toList());
    }
}
