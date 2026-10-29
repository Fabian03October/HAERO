package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.AltaInicialRequest;
import com.example.backend.farmacia.dto.AltaInicialResponse;
import com.example.backend.farmacia.dto.BajaResponse;
import com.example.backend.farmacia.dto.LoteFarmaciaResponse;
import com.example.backend.farmacia.dto.PartidaAltaInicial;
import com.example.backend.farmacia.dto.RegistrarBajaRequest;
import com.example.backend.farmacia.entity.BajaFarmacia;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaDevolucion;
import com.example.backend.farmacia.repository.BajaFarmaciaRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import com.example.backend.farmacia.repository.MovimientoFarmaciaRepository;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inventario de Farmacia: alta inicial de los estantes (HU25), existencias por lote (HU27),
 * lote que sale primero (HU30) y bajas por caducidad, daño o extravío (HU35).
 *
 * Farmacia cuenta en PIEZAS. Cada cambio de piezas queda en farmacia_movimiento.
 */
@Service
public class ServicioInventarioFarmacia {

    // Igual que en Almacén: un lote está "por caducar" si le quedan 9 meses o menos.
    static final int MESES_POR_CADUCAR = 9;
    private static final Set<String> MOTIVOS_BAJA = Set.of(BajaFarmacia.CADUCIDAD, BajaFarmacia.DANO, BajaFarmacia.EXTRAVIO);

    private final LoteFarmaciaRepository loteFarmaciaRepository;
    private final MovimientoFarmaciaRepository movimientoFarmaciaRepository;
    private final BajaFarmaciaRepository bajaRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final LoteRepository loteRepository;
    private final UsuarioRepository usuarioRepository;

    private final CodigoBarrasRepository codigoBarrasRepository;

    public ServicioInventarioFarmacia(LoteFarmaciaRepository loteFarmaciaRepository,
                                      MovimientoFarmaciaRepository movimientoFarmaciaRepository,
                                      BajaFarmaciaRepository bajaRepository,
                                      MedicamentoRepository medicamentoRepository,
                                      LoteRepository loteRepository,
                                      UsuarioRepository usuarioRepository,
                                      CodigoBarrasRepository codigoBarrasRepository) {
        this.codigoBarrasRepository = codigoBarrasRepository;
        this.loteFarmaciaRepository = loteFarmaciaRepository;
        this.movimientoFarmaciaRepository = movimientoFarmaciaRepository;
        this.bajaRepository = bajaRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.loteRepository = loteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ---------- HU42: códigos de barras desde Farmacia ----------

    /** El código como lo guarda Almacén: GTIN-14 de GS1 o el EAN-13 normal (con o sin el cero inicial). */
    public Optional<CodigoBarras> buscarCodigo(String codigo) {
        Optional<CodigoBarras> exacto = codigoBarrasRepository.findById(codigo);
        if (exacto.isPresent()) {
            return exacto;
        }
        if (codigo.length() == 14 && codigo.startsWith("0")) {
            return codigoBarrasRepository.findById(codigo.substring(1));
        }
        if (codigo.length() == 13) {
            return codigoBarrasRepository.findById("0" + codigo);
        }
        return Optional.empty();
    }

    /**
     * Liga el código de la caja a su clave la primera vez que Farmacia lo ve, para que desde
     * ahí la caja se reconozca sola. Un código que ya es de otra clave no se reasigna.
     */
    public void aprenderCodigo(String codigo, Medicamento medicamento) {
        if (codigo == null) {
            return;
        }
        if (codigo.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código escaneado es demasiado largo; escanea el código de barras de la caja");
        }
        Optional<CodigoBarras> existente = buscarCodigo(codigo);
        if (existente.isPresent()) {
            String clave = existente.get().getMedicamento().getClave();
            if (!clave.equals(medicamento.getClave())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código " + codigo + " ya es de la clave " + clave
                        + " " + existente.get().getMedicamento().getNombreGenerico() + "; revisa la caja");
            }
            return;
        }
        CodigoBarras nuevo = new CodigoBarras();
        nuevo.setCodigo(codigo);
        nuevo.setMedicamento(medicamento);
        nuevo.setProveedor("Registrado por Farmacia");
        codigoBarrasRepository.save(nuevo);
    }

    // ---------- HU27: existencias ----------

    /** Los lotes con piezas (también los vencidos, que se marcan como no disponibles). */
    public List<LoteFarmaciaResponse> existencias() {
        List<LoteFarmacia> lotes = loteFarmaciaRepository.findAllConMedicamento().stream()
                .filter(l -> l.getPiezas() > 0)
                .toList();
        Map<String, Long> fefo = lotesFefo(lotes);
        return lotes.stream().map(l -> aRespuesta(l, fefo)).toList();
    }

    /** HU30 CA1: el lote de la clave que debe salir primero, si hay alguno disponible. */
    public Optional<LoteFarmaciaResponse> loteFefo(String clave) {
        List<LoteFarmacia> lotes = loteFarmaciaRepository.findByMedicamentoClave(clave);
        Map<String, Long> fefo = lotesFefo(lotes);
        Long id = fefo.get(clave);
        return lotes.stream().filter(l -> l.getId().equals(id)).findFirst().map(l -> aRespuesta(l, fefo));
    }

    /**
     * El lote que sale primero de cada clave: con piezas, no vencido y que caduca antes
     * (a igual caducidad, el que se dio de alta antes).
     */
    static Map<String, Long> lotesFefo(List<LoteFarmacia> lotes) {
        LocalDate hoy = LocalDate.now();
        Map<String, Long> fefo = new HashMap<>();
        lotes.stream()
                .filter(l -> l.getPiezas() > 0 && !l.getCaducidad().isBefore(hoy))
                .sorted(Comparator.comparing(LoteFarmacia::getCaducidad).thenComparing(LoteFarmacia::getId))
                .forEach(l -> fefo.putIfAbsent(l.getMedicamento().getClave(), l.getId()));
        return fefo;
    }

    LoteFarmaciaResponse aRespuesta(LoteFarmacia lote, Map<String, Long> fefo) {
        LocalDate hoy = LocalDate.now();
        Medicamento medicamento = lote.getMedicamento();
        boolean vencido = lote.getCaducidad().isBefore(hoy);
        String estado = vencido ? "VENCIDO" : lote.getId().equals(fefo.get(medicamento.getClave())) ? "FEFO" : "ESPERA";
        Integer piezasPorCaja = medicamento.getPiezasPorCaja();
        Integer cajas = piezasPorCaja != null && piezasPorCaja > 0 ? lote.getPiezas() / piezasPorCaja : null;
        boolean porCaducar = !vencido && !lote.getCaducidad().isAfter(hoy.plusMonths(MESES_POR_CADUCAR));
        return new LoteFarmaciaResponse(lote.getId(), medicamento.getClave(), medicamento.getNombreGenerico(),
                piezasPorCaja, lote.getNumeroLote(), lote.getCaducidad(), lote.getPiezas(), cajas, estado, porCaducar,
                lote.getLoteAlmacen() != null ? lote.getLoteAlmacen().getId() : null);
    }

    // ---------- HU25: alta inicial ----------

    @Transactional
    public AltaInicialResponse altaInicial(AltaInicialRequest request, String nombreUsuario) {
        if (request.getPartidas() == null || request.getPartidas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un lote a la lista");
        }
        Usuario usuario = usuario(nombreUsuario);
        int piezas = 0;
        Set<Long> lotes = new java.util.HashSet<>();
        for (PartidaAltaInicial partida : request.getPartidas()) {
            String numeroLote = texto(partida.getLote());
            if (numeroLote == null || partida.getCaducidad() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada caja necesita su lote y su caducidad");
            }
            if (partida.getPiezas() == null || partida.getPiezas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las piezas contadas del lote " + numeroLote + " deben ser mayores a cero");
            }
            Medicamento medicamento = medicamento(partida.getClave());
            aprenderCodigo(texto(partida.getCodigo()), medicamento);
            // CA3: un lote vencido sí se da de alta (queda registrado), pero no estará disponible.
            LoteFarmacia lote = loteParaEntrada(medicamento, numeroLote, partida.getCaducidad(), null);
            mover(lote, MovimientoFarmacia.ALTA_INICIAL, partida.getPiezas(), null, usuario);
            piezas += partida.getPiezas();
            lotes.add(lote.getId());
        }
        return new AltaInicialResponse(lotes.size(), piezas);
    }

    /**
     * El lote de Farmacia donde entran piezas: el que ya existe con ese número o uno nuevo.
     * Se liga al lote de Almacén del que viene (o al que tenga ese número, si se conoce) para
     * la trazabilidad (HU33).
     */
    LoteFarmacia loteParaEntrada(Medicamento medicamento, String numeroLote, LocalDate caducidad, Lote loteAlmacen) {
        Optional<LoteFarmacia> existente = loteFarmaciaRepository
                .findByMedicamentoClaveAndNumeroLoteIgnoreCase(medicamento.getClave(), numeroLote);
        if (existente.isPresent()) {
            LoteFarmacia lote = existente.get();
            if (!lote.getCaducidad().equals(caducidad)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote " + numeroLote + " ya está en Farmacia con caducidad "
                        + lote.getCaducidad() + "; revisa la caducidad de la caja");
            }
            if (lote.getLoteAlmacen() == null && loteAlmacen != null) {
                lote.setLoteAlmacen(loteAlmacen);
            }
            return lote;
        }
        LoteFarmacia nuevo = new LoteFarmacia();
        nuevo.setMedicamento(medicamento);
        nuevo.setNumeroLote(numeroLote);
        nuevo.setCaducidad(caducidad);
        nuevo.setPiezas(0);
        nuevo.setLoteAlmacen(loteAlmacen != null ? loteAlmacen : loteDeAlmacen(medicamento.getClave(), numeroLote));
        return loteFarmaciaRepository.save(nuevo);
    }

    private Lote loteDeAlmacen(String clave, String numeroLote) {
        return loteRepository.findByMedicamentoClaveAndNumeroLoteIgnoreCaseAndEstatus(clave, numeroLote, "DISPONIBLE").stream()
                .findFirst().orElse(null);
    }

    /** Suma (o resta, con piezas negativas) piezas al lote y lo deja en la bitácora. */
    MovimientoFarmacia mover(LoteFarmacia lote, String tipo, int piezas, Long referenciaId, Usuario usuario) {
        int nuevas = lote.getPiezas() + piezas;
        if (nuevas < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El lote " + lote.getNumeroLote() + " solo tiene " + lote.getPiezas() + " piezas en Farmacia");
        }
        lote.setPiezas(nuevas);
        loteFarmaciaRepository.save(lote);
        MovimientoFarmacia movimiento = new MovimientoFarmacia();
        movimiento.setLote(lote);
        movimiento.setTipo(tipo);
        movimiento.setPiezas(piezas);
        movimiento.setReferenciaId(referenciaId);
        movimiento.setUsuario(usuario);
        return movimientoFarmaciaRepository.save(movimiento);
    }

    // ---------- HU35: caducados y mermas ----------

    /** CA1: los lotes vencidos con piezas, sugeridos para darse de baja (nunca se dan de baja solos). */
    public List<LoteFarmaciaResponse> sugeridosParaBaja() {
        return existencias().stream().filter(l -> "VENCIDO".equals(l.getEstado())).toList();
    }

    public List<BajaResponse> bajas() {
        return bajaRepository.findAllByOrderByFechaDesc().stream().map(ServicioInventarioFarmacia::aRespuesta).toList();
    }

    @Transactional
    public BajaResponse registrarBaja(RegistrarBajaRequest request, String nombreUsuario) {
        LoteFarmacia lote = loteFarmaciaRepository.findById(request.getLoteId() == null ? -1 : request.getLoteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese lote no está en el inventario de Farmacia"));
        String motivo = request.getMotivo() == null ? null : request.getMotivo().trim().toUpperCase();
        if (!MOTIVOS_BAJA.contains(motivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo debe ser caducidad, daño o extravío");
        }
        String detalle = texto(request.getDetalle());
        // CA3: para daño y extravío se describe qué pasó.
        if (!BajaFarmacia.CADUCIDAD.equals(motivo) && detalle == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Describe el detalle del daño o extravío");
        }
        if (request.getPiezas() == null || request.getPiezas() <= 0 || request.getPiezas() > lote.getPiezas()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las piezas deben ser mayores a cero y no más de las " + lote.getPiezas() + " que hay en el lote");
        }
        Usuario usuario = usuario(nombreUsuario);
        BajaFarmacia baja = guardarBaja(lote, request.getPiezas(), motivo, detalle, null, usuario);
        mover(lote, MovimientoFarmacia.BAJA, -request.getPiezas(), baja.getId(), usuario);
        return aRespuesta(baja);
    }

    /**
     * Registra la baja. No descuenta piezas: quien la llama decide (lo dañado de una
     * devolución nunca regresó al inventario, así que no se descuenta).
     */
    BajaFarmacia guardarBaja(LoteFarmacia lote, int piezas, String motivo, String detalle,
                             PartidaDevolucion partidaDevolucion, Usuario usuario) {
        BajaFarmacia baja = new BajaFarmacia();
        baja.setLote(lote);
        baja.setPiezas(piezas);
        baja.setMotivo(motivo);
        baja.setDetalle(detalle);
        baja.setPartidaDevolucion(partidaDevolucion);
        baja.setUsuario(usuario);
        return bajaRepository.save(baja);
    }

    static BajaResponse aRespuesta(BajaFarmacia baja) {
        LoteFarmacia lote = baja.getLote();
        return new BajaResponse(baja.getId(), lote.getMedicamento().getClave(), lote.getMedicamento().getNombreGenerico(),
                lote.getNumeroLote(), lote.getCaducidad(), baja.getPiezas(), baja.getMotivo(), baja.getDetalle(),
                baja.getFecha(), baja.getUsuario().getNombreCompleto(), baja.getPartidaDevolucion() != null);
    }

    // ---------- comunes ----------

    Medicamento medicamento(String clave) {
        String limpia = texto(clave);
        if (limpia == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la clave del medicamento");
        }
        return medicamentoRepository.findById(limpia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + limpia + " no está en el catálogo"));
    }

    Usuario usuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
