package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.usuarios.entity.Usuario;
import com.example.backend.farmacia.dto.CajaEntregaRequest;
import com.example.backend.farmacia.repository.ResurtimientoRepository;
import com.example.backend.farmacia.entity.Resurtimiento;
import com.example.backend.farmacia.repository.PartidaEntregaRepository;
import com.example.backend.farmacia.dto.EntregaPreviaResponse;
import com.example.backend.farmacia.dto.ClavePendienteResponse;
import com.example.backend.farmacia.dto.ClaveRecetaRequest;
import com.example.backend.farmacia.dto.ClaveRecetaResponse;
import com.example.backend.farmacia.dto.EntregaResponse;
import com.example.backend.farmacia.dto.PacienteRequest;
import com.example.backend.farmacia.dto.PartidaEntregadaResponse;
import com.example.backend.farmacia.dto.RecetaResponse;
import com.example.backend.farmacia.dto.RegistrarEntregaRequest;
import com.example.backend.farmacia.entity.ClaveReceta;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.EntregaRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import com.example.backend.farmacia.repository.RecetaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Entrega de recetas en Farmacia: HU28 (entrega con cajas escaneadas), HU29 (recetas
 * parciales y no dispensadas) y HU30 (regla FEFO al escanear).
 *
 * Una receta se registra una sola vez por su folio. Si queda parcial, se completa con otra
 * entrega con el mismo folio. Las entregas ya no se dan de baja por paciente en Intelisis:
 * se suman en el resumen del día (HU31).
 */
@Service
public class ServicioEntregas {

    static final String SIN_EXISTENCIA = "Sin existencia";
    private static final int LARGO_CURP = 18;

    private final RecetaRepository recetaRepository;
    private final EntregaRepository entregaRepository;
    private final LoteFarmaciaRepository loteFarmaciaRepository;
    private final ServicioInventarioFarmacia inventario;

    private final int vigenciaDias;
    private final PartidaEntregaRepository partidaEntregaRepository;
    private final ResurtimientoRepository resurtimientos;
    // Días hacia atrás en que se avisa que el paciente ya recibió la misma clave.
    static final int DIAS_ENTREGA_REPETIDA = 7;

    public ServicioEntregas(RecetaRepository recetaRepository,
                            EntregaRepository entregaRepository,
                            LoteFarmaciaRepository loteFarmaciaRepository,
                            ServicioInventarioFarmacia inventario,
                            @Value("${farmacia.recetas.vigencia-dias:30}") int vigenciaDias,
                            PartidaEntregaRepository partidaEntregaRepository,
                            ResurtimientoRepository resurtimientos) {
        this.vigenciaDias = vigenciaDias;
        this.partidaEntregaRepository = partidaEntregaRepository;
        this.resurtimientos = resurtimientos;
        this.recetaRepository = recetaRepository;
        this.entregaRepository = entregaRepository;
        this.loteFarmaciaRepository = loteFarmaciaRepository;
        this.inventario = inventario;
    }

    @Transactional
    public EntregaResponse registrar(RegistrarEntregaRequest request, String nombreUsuario) {
        String folio = ServicioInventarioFarmacia.texto(request.getFolioReceta());
        if (folio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el folio de la receta");
        }
        Usuario usuario = inventario.usuario(nombreUsuario);
        List<ClaveRecetaRequest> clavesPedidas = request.getClaves() == null ? List.of() : request.getClaves();
        List<CajaEntregaRequest> cajas = request.getCajas() == null ? List.of() : request.getCajas();

        Receta receta = recetaRepository.findByFolioIgnoreCase(folio).orElse(null);
        if (receta == null) {
            receta = nuevaReceta(folio, request.getPaciente(), clavesPedidas, usuario);
        } else if (Receta.SURTIDA.equals(receta.getEstado())) {
            // HU28 CA2: el folio no se registra dos veces; una receta surtida ya no recibe entregas.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta " + receta.getFolio() + " ya se surtió por completo");
        } else if (vencida(receta)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta " + receta.getFolio() + " venció: se surtió por primera vez el "
                    + receta.getFecha().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " y lo que faltó solo se entrega durante " + vigenciaDias + " días. El paciente necesita una receta nueva.");
        }

        Map<String, ClaveReceta> porClave = new LinkedHashMap<>();
        receta.getClaves().forEach(c -> porClave.put(c.getMedicamento().getClave(), c));

        // HU29 CA2: claves que se marcan como no dispensadas (o dejan de estarlo).
        Set<String> marcadasAhora = new HashSet<>();
        boolean cambioNoDispensada = false;
        for (ClaveRecetaRequest pedida : clavesPedidas) {
            ClaveReceta clave = porClave.get(ServicioInventarioFarmacia.texto(pedida.getClave()));
            if (clave == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + pedida.getClave() + " no está en la receta " + receta.getFolio());
            }
            if (pedida.isNoDispensada()) {
                if (clave.pendiente() == 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + pedida.getClave() + " ya se entregó completa");
                }
                clave.setNoDispensada(true);
                clave.setMotivo(motivoNoDispensada(clave, pedida.getMotivo()));
                marcadasAhora.add(clave.getMedicamento().getClave());
                cambioNoDispensada = true;
            }
        }

        if (cajas.isEmpty() && !cambioNoDispensada) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escanea al menos una caja o marca las claves que no se pueden surtir");
        }

        // Lotes asignados a cada caja. "restante" va descontando lo que toman las cajas de esta
        // misma entrega, para que la regla FEFO y la existencia se revisen con lo que queda.
        Map<String, List<LoteFarmacia>> lotesPorClave = new HashMap<>();
        Map<Long, Integer> restante = new HashMap<>();
        Map<String, Integer> nuevasPorClave = new HashMap<>();
        Map<String, PartidaEntrega> partidas = new LinkedHashMap<>();
        Entrega entrega = new Entrega();
        entrega.setReceta(receta);
        entrega.setUsuario(usuario);
        // Ventanilla: si ya se le entregó alguna de estas claves en los últimos días (otra receta, Quimio u
        // Hospitalización), se entrega solo con una justificación, que queda guardada.
        if (!interna(receta)) {
            List<EntregaPreviaResponse> previas = entregasPrevias(receta.getPacienteExpediente(),
                    cajas.stream().map(c -> ServicioInventarioFarmacia.texto(c.getClave())).filter(java.util.Objects::nonNull).toList(), receta.getFolio());
            String justificacion = ServicioInventarioFarmacia.texto(request.getJustificacionRepetida());
            if (!previas.isEmpty() && justificacion == null) {
                EntregaPreviaResponse p = previas.get(0);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya se le entregó " + p.clave() + " el "
                        + p.fecha().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + " (" + p.area()
                        + ", receta " + p.receta() + ", " + p.piezas() + " pz). Escribe la justificación para entregarlo otra vez.");
            }
            if (justificacion != null && justificacion.length() > 300) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La justificación no puede pasar de 300 caracteres");
            }
            entrega.setJustificacionRepetida(previas.isEmpty() ? null : justificacion);
        }

        for (CajaEntregaRequest caja : cajas) {
            String clave = ServicioInventarioFarmacia.texto(caja.getClave());
            ClaveReceta claveReceta = porClave.get(clave);
            if (claveReceta == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caja es de la clave " + clave + ", que no está en la receta");
            }
            if (marcadasAhora.contains(clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + clave + " está marcada como no dispensada y tiene cajas escaneadas");
            }
            if (caja.getPiezas() == null || caja.getPiezas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las piezas de cada caja de la clave " + clave);
            }
            List<LoteFarmacia> lotes = lotesPorClave.computeIfAbsent(clave, loteFarmaciaRepository::findByMedicamentoClave);
            lotes.forEach(l -> restante.putIfAbsent(l.getId(), l.getPiezas()));
            String numeroLote = ServicioInventarioFarmacia.texto(caja.getLote());
            nuevasPorClave.merge(clave, caja.getPiezas(), Integer::sum);

            if (numeroLote != null) {
                // Caja con lote (GS1): sale de ese lote, que debe ser el FEFO.
                LoteFarmacia lote = elegirLote(clave, numeroLote, lotes, restante);
                int disponibles = restante.get(lote.getId());
                if (caja.getPiezas() > disponibles) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El lote " + lote.getNumeroLote() + " solo tiene " + disponibles + " piezas disponibles en Farmacia");
                }
                tomar(partidas, entrega, claveReceta, lote, caja.getPiezas(), restante);
                continue;
            }
            // Caja sin lote: las piezas se toman en orden FEFO; si el primer lote no alcanza,
            // el resto sale del siguiente.
            int faltan = caja.getPiezas();
            while (faltan > 0) {
                LoteFarmacia lote;
                try {
                    lote = elegirLote(clave, null, lotes, restante);
                } catch (ResponseStatusException sinExistencia) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay existencia suficiente de la clave " + clave
                            + " en Farmacia: faltan " + faltan + " piezas");
                }
                int piezas = Math.min(faltan, restante.get(lote.getId()));
                tomar(partidas, entrega, claveReceta, lote, piezas, restante);
                faltan -= piezas;
            }
        }

        // HU28 CA4: no se entrega más de lo indicado.
        for (Map.Entry<String, Integer> nuevas : nuevasPorClave.entrySet()) {
            ClaveReceta clave = porClave.get(nuevas.getKey());
            if (nuevas.getValue() > clave.pendiente()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se entregarían " + nuevas.getValue() + " piezas de la clave "
                        + nuevas.getKey() + " y la receta solo tiene " + clave.pendiente() + " pendientes");
            }
            clave.setPiezasEntregadas(clave.getPiezasEntregadas() + nuevas.getValue());
            // Si se entrega una clave que había quedado como no dispensada, deja de estarlo.
            clave.setNoDispensada(false);
            clave.setMotivo(null);
        }

        receta.setEstado(estado(receta));
        recetaRepository.save(receta);

        if (!partidas.isEmpty()) {
            entrega.getPartidas().addAll(partidas.values());
            entregaRepository.save(entrega);
            for (PartidaEntrega partida : entrega.getPartidas()) {
                inventario.mover(partida.getLote(), MovimientoFarmacia.ENTREGA, -partida.getPiezas(), partida.getId(), usuario);
            }
        }
        return new EntregaResponse(entrega.getId(), entrega.folio(), entrega.getFecha(),
                entrega.getPartidas().stream().map(ServicioEntregas::aRespuesta).toList(), aRespuesta(receta));
    }

    /** Descuenta del "restante" del lote y lo suma a su partida (una por clave y lote). */
    private static void tomar(Map<String, PartidaEntrega> partidas, Entrega entrega, ClaveReceta claveReceta,
                              LoteFarmacia lote, int piezas, Map<Long, Integer> restante) {
        restante.put(lote.getId(), restante.get(lote.getId()) - piezas);
        PartidaEntrega partida = partidas.computeIfAbsent(claveReceta.getMedicamento().getClave() + "|" + lote.getId(), k -> {
            PartidaEntrega nueva = new PartidaEntrega();
            nueva.setEntrega(entrega);
            nueva.setClaveReceta(claveReceta);
            nueva.setLote(lote);
            nueva.setPiezas(0);
            return nueva;
        });
        partida.setPiezas(partida.getPiezas() + piezas);
    }

    private Receta nuevaReceta(String folio, PacienteRequest paciente, List<ClaveRecetaRequest> claves, Usuario usuario) {
        String nombre = paciente == null ? null : ServicioInventarioFarmacia.texto(paciente.getNombre());
        if (nombre == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el paciente de la receta");
        }
        if (claves.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos una clave de la receta");
        }
        String curp = ServicioInventarioFarmacia.texto(paciente.getCurp());
        if (curp != null && curp.length() != LARGO_CURP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La CURP debe tener 18 caracteres");
        }
        Receta receta = new Receta();
        receta.setFolio(folio);
        receta.setPacienteNombre(nombre);
        receta.setPacienteExpediente(ServicioInventarioFarmacia.texto(paciente.getExpediente()));
        receta.setPacienteCurp(curp == null ? null : curp.toUpperCase(Locale.ROOT));
        receta.setEstado(Receta.PARCIAL);
        receta.setUsuario(usuario);
        Set<String> vistas = new HashSet<>();
        for (ClaveRecetaRequest pedida : claves) {
            Medicamento medicamento = inventario.medicamento(pedida.getClave());
            if (!vistas.add(medicamento.getClave())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + medicamento.getClave() + " viene dos veces en la receta");
            }
            if (pedida.getPiezasIndicadas() == null || pedida.getPiezasIndicadas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las piezas de la clave " + medicamento.getClave());
            }
            ClaveReceta clave = new ClaveReceta();
            clave.setReceta(receta);
            clave.setMedicamento(medicamento);
            clave.setPiezasIndicadas(pedida.getPiezasIndicadas());
            receta.getClaves().add(clave);
        }
        return receta;
    }

    /**
     * HU30: la caja con lote (código GS1) debe ser del lote que sale primero; sin lote se
     * toma ese lote. Un lote vencido nunca se entrega. A igual caducidad no se bloquea.
     */
    static LoteFarmacia elegirLote(String clave, String numeroLote, List<LoteFarmacia> lotes, Map<Long, Integer> restante) {
        LocalDate hoy = LocalDate.now();
        LoteFarmacia fefo = lotes.stream()
                .filter(l -> restante.getOrDefault(l.getId(), l.getPiezas()) > 0 && !l.getCaducidad().isBefore(hoy))
                .min(Comparator.comparing(LoteFarmacia::getCaducidad).thenComparing(LoteFarmacia::getId))
                .orElse(null);
        if (numeroLote == null) {
            if (fefo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay existencia disponible de la clave " + clave + " en Farmacia");
            }
            return fefo;
        }
        LoteFarmacia lote = lotes.stream().filter(l -> l.getNumeroLote().equalsIgnoreCase(numeroLote)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El lote " + numeroLote + " de la clave " + clave + " no está en el inventario de Farmacia"));
        if (lote.getCaducidad().isBefore(hoy)) {
            // HU30 CA4.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote " + lote.getNumeroLote() + " venció el "
                    + lote.getCaducidad() + ". No se puede entregar; apártalo para darlo de baja");
        }
        if (fefo != null && fefo.getCaducidad().isBefore(lote.getCaducidad())) {
            // HU30 CA2.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe salir primero el lote " + fefo.getNumeroLote()
                    + " (caduca el " + fefo.getCaducidad() + ")");
        }
        return lote;
    }

    /** HU29 CA2: sin motivo escrito y sin existencia de la clave, el motivo es "Sin existencia". */
    private String motivoNoDispensada(ClaveReceta clave, String motivo) {
        String escrito = ServicioInventarioFarmacia.texto(motivo);
        if (escrito != null) {
            return escrito;
        }
        LocalDate hoy = LocalDate.now();
        boolean hayExistencia = loteFarmaciaRepository.findByMedicamentoClave(clave.getMedicamento().getClave()).stream()
                .anyMatch(l -> l.getPiezas() > 0 && !l.getCaducidad().isBefore(hoy));
        if (hayExistencia) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay existencia de la clave " + clave.getMedicamento().getClave()
                    + " en Farmacia: escribe por qué no se dispensa");
        }
        return SIN_EXISTENCIA;
    }

    static String estado(Receta receta) {
        boolean completa = receta.getClaves().stream().allMatch(c -> c.pendiente() == 0);
        if (completa) {
            return Receta.SURTIDA;
        }
        boolean nadaEntregado = receta.getClaves().stream().allMatch(c -> c.getPiezasEntregadas() == 0);
        boolean pendientesNoDispensadas = receta.getClaves().stream().filter(c -> c.pendiente() > 0).allMatch(ClaveReceta::isNoDispensada);
        return nadaEntregado && pendientesNoDispensadas ? Receta.NO_DISPENSADA : Receta.PARCIAL;
    }

    // ---------- HU29: consulta de recetas ----------

    public List<RecetaResponse> recetas(List<String> estados) {
        return recetaRepository.findByEstadoInAndFolioNotLikeOrderByFechaDesc(estados, ServicioCarrito.PREFIJO_FOLIO + "%").stream().map(this::aRespuesta).toList();
    }

    public RecetaResponse receta(String folio) {
        return recetaRepository.findByFolioIgnoreCase(folio).map(this::aRespuesta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una receta con el folio " + folio));
    }

    /** CA5: claves con más piezas pendientes en recetas parciales o no dispensadas. */
    public List<ClavePendienteResponse> clavesPendientes() {
        Map<String, int[]> totales = new LinkedHashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (Receta receta : recetaRepository.findByEstadoInAndFolioNotLikeOrderByFechaDesc(List.of(Receta.PARCIAL, Receta.NO_DISPENSADA), ServicioCarrito.PREFIJO_FOLIO + "%")) {
            for (ClaveReceta clave : receta.getClaves()) {
                if (clave.pendiente() > 0) {
                    int[] total = totales.computeIfAbsent(clave.getMedicamento().getClave(), k -> new int[2]);
                    total[0] += clave.pendiente();
                    total[1]++;
                    nombres.put(clave.getMedicamento().getClave(), clave.getMedicamento().getNombreGenerico());
                }
            }
        }
        List<ClavePendienteResponse> lista = new ArrayList<>();
        totales.forEach((clave, t) -> lista.add(new ClavePendienteResponse(clave, nombres.get(clave), t[0], t[1])));
        lista.sort(Comparator.comparingInt(ClavePendienteResponse::getPiezas).reversed());
        return lista;
    }

    /**
     * Una receta de Ventanilla a medias vence a los N días (farmacia.recetas.vigencia-dias, 30 por omisión):
     * el paciente ya no puede regresar meses después por lo que faltó. Las internas (Hospitalización,
     * Quimioterapia, Quirófano) no vencen: son del día.
     */
    boolean vencida(Receta receta) {
        return !interna(receta) && receta.getFecha() != null && inicioDelSurtido(receta).isBefore(LocalDateTime.now().minusDays(vigenciaDias));
    }

    /** Desde cuándo corre la vigencia: la primera entrega o, si se resurtió, el último resurtido. */
    private LocalDateTime inicioDelSurtido(Receta receta) {
        return receta.getId() == null ? receta.getFecha()
                : resurtimientos.findFirstByRecetaIdOrderByFechaDesc(receta.getId()).map(Resurtimiento::getFecha).orElse(receta.getFecha());
    }

    /**
     * Resurtir una receta de Ventanilla ya surtida (paciente crónico que regresa con la misma receta): se
     * reabre con las mismas cantidades del médico y se surte con el flujo normal. Queda registrado el motivo.
     */
    @Transactional
    public RecetaResponse resurtir(String folio, String motivo, String nombreUsuario) {
        Receta receta = recetaRepository.findByFolioIgnoreCase(ServicioInventarioFarmacia.texto(folio))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una receta con el folio " + folio + " en Farmacia"));
        if (interna(receta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo las recetas de Ventanilla se resurten");
        }
        if (!Receta.SURTIDA.equals(receta.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La receta " + receta.getFolio() + " todavía tiene piezas pendientes: termina de surtirla");
        }
        String elMotivo = ServicioInventarioFarmacia.texto(motivo);
        if (elMotivo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo del resurtido");
        }
        if (elMotivo.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 300 caracteres");
        }
        for (ClaveReceta clave : receta.getClaves()) {
            int porSurtido = clave.getPiezasPorSurtido() != null ? clave.getPiezasPorSurtido() : clave.getPiezasIndicadas();
            clave.setPiezasPorSurtido(porSurtido);
            clave.setPiezasIndicadas(clave.getPiezasEntregadas() + porSurtido);
            clave.setNoDispensada(false);
            clave.setMotivo(null);
        }
        receta.setEstado(Receta.PARCIAL);
        recetaRepository.save(receta);
        Resurtimiento r = new Resurtimiento();
        r.setReceta(receta);
        r.setNumero((int) resurtimientos.countByRecetaId(receta.getId()) + 1);
        r.setMotivo(elMotivo);
        r.setUsuario(inventario.usuario(nombreUsuario));
        r.setFecha(LocalDateTime.now());
        resurtimientos.saveAndFlush(r);
        return aRespuesta(receta);
    }

    /** Recetas que arma SICOMED para Hospitalización, Quimioterapia o Quirófano (no son de Ventanilla). */
    static boolean interna(Receta receta) {
        return !"Ventanilla".equals(area(receta.getFolio()));
    }

    static String area(String folioReceta) {
        String folio = folioReceta == null ? "" : folioReceta.toUpperCase();
        if (folio.startsWith(ServicioCarrito.PREFIJO_FOLIO)) return "Hospitalización";
        if (folio.startsWith(ServicioQuimioterapia.PREFIJO_FOLIO)) return "Quimioterapia";
        if (folio.startsWith(ServicioCajasQuirofano.PREFIJO_FOLIO)) return "Quirófano";
        return "Ventanilla";
    }

    /**
     * Lo que se le entregó al paciente de esas claves en los últimos 7 días, en cualquier área, sin contar la
     * receta indicada (la que se está surtiendo). Una línea por entrega y clave, con lo que no se devolvió.
     */
    @Transactional(readOnly = true)
    public List<EntregaPreviaResponse> entregasPrevias(String expediente, java.util.Collection<String> claves, String excluirReceta) {
        String exp = ServicioInventarioFarmacia.texto(expediente);
        if (exp == null || claves == null || claves.isEmpty()) {
            return List.of();
        }
        Map<String, EntregaPreviaResponse> porEntregaYClave = new LinkedHashMap<>();
        for (PartidaEntrega p : partidaEntregaRepository
                .previasDelPaciente(
                        exp, LocalDateTime.now().minusDays(DIAS_ENTREGA_REPETIDA), claves)) {
            Receta r = p.getEntrega().getReceta();
            int netas = p.getPiezas() - (p.getPiezasDevueltas() == null ? 0 : p.getPiezasDevueltas());
            if (netas <= 0 || (excluirReceta != null && excluirReceta.equalsIgnoreCase(r.getFolio()))) {
                continue;
            }
            Medicamento m = p.getClaveReceta().getMedicamento();
            String llave = p.getEntrega().getId() + "|" + m.getClave();
            EntregaPreviaResponse antes = porEntregaYClave.get(llave);
            porEntregaYClave.put(llave, new EntregaPreviaResponse(p.getEntrega().getFecha(), p.getEntrega().folio(), r.getFolio(),
                    area(r.getFolio()), m.getClave(), m.getNombreGenerico(), netas + (antes == null ? 0 : antes.piezas())));
        }
        return List.copyOf(porEntregaYClave.values());
    }

    private RecetaResponse aRespuesta(Receta receta) {
        List<Entrega> entregas = receta.getId() == null ? List.of() : entregaRepository.findByRecetaIdOrderByFechaAsc(receta.getId());
        String despachador = entregas.isEmpty() ? receta.getUsuario().getNombreCompleto()
                : entregas.get(entregas.size() - 1).getUsuario().getNombreCompleto();
        List<ClaveRecetaResponse> claves = receta.getClaves().stream().map(c -> new ClaveRecetaResponse(
                c.getMedicamento().getClave(), c.getMedicamento().getNombreGenerico(), c.getMedicamento().getPiezasPorCaja(),
                c.getPiezasIndicadas(), c.getPiezasEntregadas(), c.pendiente(), c.isNoDispensada(), c.getMotivo())).toList();
        return new RecetaResponse(receta.getFolio(), receta.getPacienteExpediente(), receta.getPacienteNombre(), receta.getPacienteCurp(),
                receta.getFecha(), despachador, receta.getEstado(), claves,
                !Receta.SURTIDA.equals(receta.getEstado()) && vencida(receta),
                receta.getId() == null ? 0 : resurtimientos.countByRecetaId(receta.getId()),
                entregas.isEmpty() ? null : entregas.get(entregas.size() - 1).getFecha());
    }

    static PartidaEntregadaResponse aRespuesta(PartidaEntrega partida) {
        LoteFarmacia lote = partida.getLote();
        return new PartidaEntregadaResponse(partida.getId(), lote.getMedicamento().getClave(), lote.getMedicamento().getNombreGenerico(),
                lote.getNumeroLote(), lote.getCaducidad(), partida.getPiezas(), partida.getPiezasDevueltas());
    }
}
