package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.ArmarRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CajaDadaDeBaja;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CajaResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.CodigoResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.ContenidoRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.EntregarRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.EventoCaja;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.GuardarCajaRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.LoteEnCaja;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.MedicoResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.MotivoCajaRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.PartidaVale;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.RecibirRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.RenglonCaja;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.SacarRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.UsoRequest;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.UsoResponse;
import com.example.backend.farmacia.dto.CajasQuirofanoDtos.ValeResponse;
import com.example.backend.farmacia.entity.ClaveReceta;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.MovimientoFarmacia;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.EntregaRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import com.example.backend.farmacia.repository.RecetaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cajas de anestesia de quirófano (vales de medicamentos para quirófano).
 *
 *  - Cada caja tiene un código de barras y un listado fijo de claves y piezas. Cambiar el listado
 *    pide justificación y queda en el historial.
 *  - Armar: las piezas salen del estante de Farmacia a la caja (movimiento CAJA_QUIROFANO). Un
 *    frasco sin código se mete eligiendo la clave del listado y el lote de la lista de Farmacia
 *    (o sin lote: FEFO). Los medicamentos de red fría y los lotes vencidos no entran.
 *  - Entregar: abre el vale del turno (sala y médico anestesiólogo). Solo sale completa.
 *  - Recibir: lo utilizado por paciente (expediente) con la indicación médica en PDF. Cada paciente
 *    queda como una entrega normal de Farmacia ("QX-…"): entra al resumen del día, a la baja por
 *    volumen y a la trazabilidad. Lo que regresa se queda en la caja para la siguiente salida.
 */
@Service
public class ServicioCajasQuirofano {

    static final String MOVIMIENTO_CAJA = "CAJA_QUIROFANO";
    static final String PREFIJO_FOLIO = "QX-";
    static final Set<String> TURNOS = Set.of("MATUTINO", "VESPERTINO", "NOCTURNO", "FIN_DE_SEMANA");
    private static final long TAMANO_MAXIMO_PDF = 10L * 1024 * 1024;
    private static final int VALES_EN_RESPUESTA = 10;

    private final JdbcTemplate haero;
    private final ServicioInventarioFarmacia inventario;
    private final LoteFarmaciaRepository loteFarmaciaRepository;
    private final RecetaRepository recetaRepository;
    private final EntregaRepository entregaRepository;

    public ServicioCajasQuirofano(JdbcTemplate haero,
                                  ServicioInventarioFarmacia inventario,
                                  LoteFarmaciaRepository loteFarmaciaRepository,
                                  RecetaRepository recetaRepository,
                                  EntregaRepository entregaRepository) {
        this.haero = haero;
        this.inventario = inventario;
        this.loteFarmaciaRepository = loteFarmaciaRepository;
        this.recetaRepository = recetaRepository;
        this.entregaRepository = entregaRepository;
    }

    // ---------- Consulta ----------

    public List<CajaResponse> listar() {
        List<String> codigos = haero.queryForList("SELECT codigo FROM quirofano_caja WHERE activa = 1 ORDER BY servicio, nombre", String.class);
        return codigos.stream().map(this::detalle).toList();
    }

    public CajaResponse detalle(String codigo) {
        Caja caja = caja(codigo, false);
        Map<String, Contenido> contenido = contenido(caja.id);
        List<Pieza> piezas = piezas(caja.id);
        List<RenglonCaja> renglones = new ArrayList<>();
        int faltantes = 0;
        for (Contenido c : contenido.values()) {
            List<LoteEnCaja> lotes = piezas.stream().filter(p -> p.clave.equals(c.clave))
                    .map(p -> new LoteEnCaja(p.lote, p.caducidad, p.piezas)).toList();
            int tiene = lotes.stream().mapToInt(LoteEnCaja::piezas).sum();
            // Lo de red fría no se guarda en la caja: no cuenta como faltante.
            int falta = c.aparte ? 0 : Math.max(0, c.cantidad - tiene);
            faltantes += falta;
            renglones.add(new RenglonCaja(c.clave, c.mostrado(), c.cantidad, tiene, falta, lotes, c.aparte, c.descripcion, c.nombre));
        }
        List<ValeResponse> vales = vales(caja.id);
        ValeResponse abierto = vales.stream().filter(v -> v.recibidoEn() == null).findFirst().orElse(null);
        String estado = abierto != null ? "ENTREGADA" : faltantes == 0 ? "COMPLETA" : "INCOMPLETA";
        List<EventoCaja> historial = haero.query("""
                SELECT TOP 50 e.accion, e.detalle, e.fecha, u.nombre_completo FROM quirofano_caja_evento e
                JOIN usuario u ON u.id = e.usuario_id WHERE e.caja_id = ? ORDER BY e.fecha DESC, e.id DESC
                """, (rs, n) -> new EventoCaja(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime(), rs.getString(4)), caja.id);
        return new CajaResponse(caja.codigo, caja.nombre, caja.servicio, estado, faltantes, renglones, abierto, vales, historial);
    }

    /**
     * Lo que está dentro de las cajas por lote de Farmacia: sigue siendo de Farmacia, así que la
     * pantalla de existencias lo muestra aparte ("en cajas") y entra a los avisos de caducidad.
     */
    public List<PiezasEnCaja> enCajas() {
        Map<Long, PiezasEnCaja> porLote = new LinkedHashMap<>();
        haero.query("""
                SELECT f.id, f.medicamento_clave, m.nombre_generico, f.numero_lote, f.caducidad, c.codigo, c.nombre, p.piezas
                FROM quirofano_caja_pieza p
                JOIN quirofano_caja c ON c.id = p.caja_id
                JOIN farmacia_lote f ON f.id = p.farmacia_lote_id
                JOIN medicamento m ON m.clave = f.medicamento_clave
                WHERE p.piezas > 0 AND c.activa = 1
                ORDER BY f.caducidad, f.medicamento_clave, c.codigo
                """, rs -> {
            long id = rs.getLong(1);
            PiezasEnCaja actual = porLote.get(id);
            String caja = rs.getString(6) + " · " + rs.getString(7) + " (" + rs.getInt(8) + ")";
            if (actual == null) {
                porLote.put(id, new PiezasEnCaja(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5).toLocalDate(),
                        rs.getInt(8), new ArrayList<>(List.of(caja))));
            } else {
                actual.cajas().add(caja);
                porLote.put(id, new PiezasEnCaja(id, actual.clave(), actual.medicamento(), actual.lote(), actual.caducidad(),
                        actual.piezas() + rs.getInt(8), actual.cajas()));
            }
        });
        return new ArrayList<>(porLote.values());
    }

    public record PiezasEnCaja(Long loteId, String clave, String medicamento, String lote, LocalDate caducidad, int piezas,
                               List<String> cajas) {
    }

    /** El código de barras de un medicamento, para reconocerlo al armar la caja (HU42). */
    public CodigoResponse codigo(String codigo) {
        String limpio = ServicioInventarioFarmacia.texto(codigo);
        if (limpio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escanea el código del medicamento");
        }
        Medicamento medicamento = inventario.buscarCodigo(limpio).map(CodigoBarras::getMedicamento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El código " + limpio + " todavía no está registrado. Elige de qué medicamento es."));
        return new CodigoResponse(medicamento.getClave(), medicamento.getNombreGenerico());
    }

    /** La indicación médica (PDF) de un paciente del vale. */
    public Indicacion indicacion(Long valeId, String expediente) {
        return haero.query("""
                        SELECT documento_nombre, documento FROM quirofano_vale_indicacion WHERE vale_id = ? AND paciente_expediente = ?
                        """, (rs, n) -> new Indicacion(rs.getString(1), rs.getBytes(2)), valeId, expediente == null ? "" : expediente.trim())
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese paciente no tiene indicación médica en el vale"));
    }

    /** Médicos de Intelisis guardados en HAERO, anestesiólogos primero, para elegir quién recibe la caja. */
    public List<MedicoResponse> medicos(String nombre) {
        String texto = ServicioInventarioFarmacia.texto(nombre);
        if (texto == null || texto.length() < 2) {
            return List.of();
        }
        return haero.query("""
                SELECT TOP 15 usuario, nombre, especialidad, cedula FROM hosp_medico
                WHERE nombre LIKE ? OR usuario LIKE ?
                ORDER BY CASE WHEN especialidad LIKE '%ANESTE%' THEN 0 ELSE 1 END, nombre
                """, (rs, n) -> new MedicoResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                "%" + texto + "%", texto + "%");
    }

    public record Indicacion(String nombre, byte[] contenido) {
    }

    // ---------- Catálogo de cajas ----------

    @Transactional
    public CajaResponse crear(GuardarCajaRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        String nombre = obligatorio(request.nombre(), "Escribe el nombre de la caja");
        String servicio = obligatorio(request.servicio(), "Escribe el servicio o sala de la caja");
        List<Contenido> contenido = validarContenido(request.contenido());
        String codigo = ServicioInventarioFarmacia.texto(request.codigo());
        if (codigo != null) {
            codigo = codigo.toUpperCase(Locale.ROOT);
            if (codigo.length() > 20) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de la caja no puede pasar de 20 caracteres");
            }
            if (existeCodigo(codigo)) {
                boolean deBaja = !haero.queryForList("SELECT id FROM quirofano_caja WHERE codigo = ? AND activa = 0", Long.class, codigo).isEmpty();
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una caja con el código " + codigo
                        + (deBaja ? " (dada de baja: reactívala en lugar de crear otra)" : ""));
            }
        }
        // Sin código propio se usa CAJA-001…; mientras se sabe el id lleva uno temporal.
        String temporal = codigo != null ? codigo : "T-" + UUID.randomUUID().toString().substring(0, 16);
        Long id = haero.queryForObject("""
                INSERT INTO quirofano_caja (codigo, nombre, servicio, creada_en, usuario_id) OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """, Long.class, temporal, nombre, servicio, ahora(), usuario.getId());
        if (codigo == null) {
            codigo = String.format("CAJA-%03d", id);
            haero.update("UPDATE quirofano_caja SET codigo = ? WHERE id = ?", codigo, id);
        }
        guardarContenido(id, contenido);
        evento(id, "ALTA", "Listado: " + resumen(contenido), usuario);
        return detalle(codigo);
    }

    @Transactional
    public CajaResponse cambiar(String codigo, GuardarCajaRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        if (valeAbierto(caja.id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja está en quirófano; cámbiala cuando regrese");
        }
        String nombre = obligatorio(request.nombre(), "Escribe el nombre de la caja");
        String servicio = obligatorio(request.servicio(), "Escribe el servicio o sala de la caja");
        List<Contenido> nuevo = validarContenido(request.contenido());
        Map<String, Contenido> anterior = contenido(caja.id);
        boolean cambioListado = !resumen(new ArrayList<>(anterior.values())).equals(resumen(nuevo));
        String justificacion = ServicioInventarioFarmacia.texto(request.justificacion());
        if (cambioListado && justificacion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la justificación del cambio del listado");
        }
        // Una clave que sale del listado no puede tener piezas dentro de la caja.
        Set<String> claves = new HashSet<>();
        nuevo.forEach(c -> claves.add(c.clave));
        for (Pieza pieza : piezas(caja.id)) {
            if (!claves.contains(pieza.clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caja todavía trae " + pieza.piezas + " piezas de " + pieza.clave
                        + " (lote " + pieza.lote + "). Sácalas primero para quitarla del listado");
            }
        }
        haero.update("UPDATE quirofano_caja SET nombre = ?, servicio = ? WHERE id = ?", nombre, servicio, caja.id);
        if (cambioListado) {
            haero.update("DELETE FROM quirofano_caja_contenido WHERE caja_id = ?", caja.id);
            guardarContenido(caja.id, nuevo);
            evento(caja.id, "CAMBIO_LISTADO", justificacion + " · antes: " + resumen(new ArrayList<>(anterior.values()))
                    + " · ahora: " + resumen(nuevo), usuario);
        } else {
            if (!nombre.equals(caja.nombre) || !servicio.equals(caja.servicio)) {
                evento(caja.id, "CAMBIO_DATOS", nombre + " · " + servicio, usuario);
            }
            // Solo se reescribieron nombres: no cambia lo que lleva la caja, no pide justificación.
            List<String> renombrados = new ArrayList<>();
            for (Contenido c : nuevo) {
                Contenido antes = anterior.get(c.clave);
                if (antes != null && !Objects.equals(antes.descripcion, c.descripcion)) {
                    haero.update("UPDATE quirofano_caja_contenido SET descripcion = ? WHERE caja_id = ? AND medicamento_clave = ?",
                            c.descripcion, caja.id, c.clave);
                    renombrados.add(c.clave + ": " + antes.mostrado() + " → " + c.mostrado());
                }
            }
            if (!renombrados.isEmpty()) {
                evento(caja.id, "CAMBIO_NOMBRES", String.join(" · ", renombrados), usuario);
            }
        }
        return detalle(caja.codigo);
    }

    /**
     * Da de baja la caja (no se borra: su historial de vales, pacientes y entregas sigue siendo la
     * trazabilidad). No puede estar en quirófano; lo que trae regresa al estante de Farmacia por lote
     * y deja de aparecer para armar, entregar y recibir. Su código queda reservado.
     */
    @Transactional
    public void darDeBaja(String codigo, MotivoCajaRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        if (valeAbierto(caja.id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja " + caja.codigo + " está en quirófano; dala de baja cuando regrese");
        }
        String motivo = motivo(request, "Escribe por qué se da de baja la caja");
        List<String> regresadas = new ArrayList<>();
        for (Pieza pieza : piezas(caja.id)) {
            LoteFarmacia lote = lote(pieza.loteId);
            sumarPieza(caja.id, lote.getId(), -pieza.piezas);
            inventario.mover(lote, MOVIMIENTO_CAJA, pieza.piezas, caja.id, usuario);
            regresadas.add(pieza.clave + " lote " + pieza.lote + " (" + pieza.piezas + ")");
        }
        haero.update("UPDATE quirofano_caja SET activa = 0 WHERE id = ?", caja.id);
        evento(caja.id, "BAJA", motivo + (regresadas.isEmpty() ? " · estaba vacía"
                : " · regresó al estante: " + String.join(", ", regresadas)), usuario);
    }

    /** Vuelve a usar una caja dada de baja (por ejemplo, si fue por error). Regresa vacía: hay que armarla. */
    @Transactional
    public CajaResponse reactivar(String codigo, MotivoCajaRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        String limpio = obligatorio(codigo, "Indica el código de la caja").toUpperCase(Locale.ROOT);
        String motivo = motivo(request, "Escribe por qué se vuelve a usar la caja");
        List<Long> ids = haero.queryForList("SELECT id FROM quirofano_caja WITH (UPDLOCK, ROWLOCK) WHERE codigo = ? AND activa = 0",
                Long.class, limpio);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una caja dada de baja con el código " + limpio);
        }
        haero.update("UPDATE quirofano_caja SET activa = 1 WHERE id = ?", ids.get(0));
        evento(ids.get(0), "REACTIVADA", motivo, usuario);
        return detalle(limpio);
    }

    /** Cajas dadas de baja, con quién, cuándo y por qué (la baja más reciente de cada una). */
    public List<CajaDadaDeBaja> dadasDeBaja() {
        return haero.query("""
                SELECT c.codigo, c.nombre, c.servicio, e.fecha, u.nombre_completo, e.detalle
                FROM quirofano_caja c
                OUTER APPLY (SELECT TOP 1 x.fecha, x.detalle, x.usuario_id FROM quirofano_caja_evento x
                             WHERE x.caja_id = c.id AND x.accion = 'BAJA' ORDER BY x.fecha DESC, x.id DESC) e
                LEFT JOIN usuario u ON u.id = e.usuario_id
                WHERE c.activa = 0 ORDER BY e.fecha DESC, c.codigo
                """, (rs, n) -> new CajaDadaDeBaja(rs.getString(1), rs.getString(2), rs.getString(3), fecha(rs.getTimestamp(4)),
                rs.getString(5), rs.getString(6)));
    }

    private static String motivo(MotivoCajaRequest request, String mensaje) {
        String motivo = obligatorio(request == null ? null : request.motivo(), mensaje);
        if (motivo.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 500 caracteres");
        }
        return motivo;
    }

    // ---------- Armar ----------

    @Transactional
    public CajaResponse armar(String codigo, ArmarRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        if (valeAbierto(caja.id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja está en quirófano; se repone cuando regrese");
        }
        String clave = obligatorio(request.clave(), "Elige el medicamento del listado de la caja");
        Contenido renglon = contenido(caja.id).get(clave);
        if (renglon == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + clave + " no está en el listado de la caja " + caja.codigo);
        }
        if (renglon.aparte || esRedFria(clave)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, clave + " es de red fría: no se guarda en la caja, sale del refrigerador al entregarla");
        }
        int piezas = request.piezas() == null ? 0 : request.piezas();
        int tiene = piezas(caja.id).stream().filter(p -> p.clave.equals(clave)).mapToInt(p -> p.piezas).sum();
        int falta = renglon.cantidad - tiene;
        if (piezas <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe las piezas que metes a la caja");
        }
        if (piezas > falta) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A la caja solo le faltan " + Math.max(0, falta) + " piezas de " + clave);
        }
        // El código escaneado se aprende para la próxima vez (si era nuevo).
        String codigoBarras = ServicioInventarioFarmacia.texto(request.codigo());
        if (codigoBarras != null) {
            inventario.aprenderCodigo(codigoBarras, inventario.medicamento(clave));
        }

        List<String> detalle = tomarDelEstante(caja.id, clave, ServicioInventarioFarmacia.texto(request.lote()), piezas, usuario);
        evento(caja.id, "ARMADO", String.join(", ", detalle), usuario);
        return detalle(caja.codigo);
    }

    /** Pasa piezas del estante de Farmacia a la caja: del lote elegido (si es el FEFO) o en orden FEFO. */
    private List<String> tomarDelEstante(Long cajaId, String clave, String numeroLote, int piezas, Usuario usuario) {
        List<LoteFarmacia> lotes = loteFarmaciaRepository.findByMedicamentoClave(clave);
        Map<Long, Integer> restante = new java.util.HashMap<>();
        lotes.forEach(l -> restante.put(l.getId(), l.getPiezas()));
        List<String> detalle = new ArrayList<>();
        int faltan = piezas;
        while (faltan > 0) {
            LoteFarmacia lote;
            if (numeroLote != null) {
                // Lote elegido (escaneado o de la lista): el que sale primero del estante, igual que en ventanilla.
                lote = ServicioEntregas.elegirLote(clave, numeroLote, lotes, restante);
            } else {
                try {
                    lote = ServicioEntregas.elegirLote(clave, null, lotes, restante);
                } catch (ResponseStatusException sinExistencia) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay existencia suficiente de " + clave
                            + " en Farmacia: faltan " + faltan + " piezas");
                }
            }
            int disponibles = restante.get(lote.getId());
            int tomadas = Math.min(faltan, disponibles);
            if (numeroLote != null && tomadas < faltan) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote " + lote.getNumeroLote() + " solo tiene "
                        + disponibles + " piezas en Farmacia");
            }
            restante.put(lote.getId(), disponibles - tomadas);
            inventario.mover(lote, MOVIMIENTO_CAJA, -tomadas, cajaId, usuario);
            sumarPieza(cajaId, lote.getId(), tomadas);
            detalle.add(clave + " lote " + lote.getNumeroLote() + " (" + tomadas + ")");
            faltan -= tomadas;
        }
        return detalle;
    }

    /** Regresa piezas de la caja al estante de Farmacia (por ejemplo, un lote por caducar o vencido). */
    @Transactional
    public CajaResponse sacar(String codigo, SacarRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        if (valeAbierto(caja.id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja está en quirófano");
        }
        String clave = obligatorio(request.clave(), "Indica la clave que sacas de la caja");
        String numeroLote = obligatorio(request.lote(), "Indica el lote que sacas de la caja");
        String motivo = obligatorio(request.motivo(), "Escribe por qué sacas las piezas de la caja");
        Pieza pieza = piezas(caja.id).stream()
                .filter(p -> p.clave.equals(clave) && p.lote.equalsIgnoreCase(numeroLote)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caja no trae el lote " + numeroLote + " de " + clave));
        int piezas = request.piezas() == null ? 0 : request.piezas();
        if (piezas <= 0 || piezas > pieza.piezas) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las piezas deben ser mayores a cero y no más de las " + pieza.piezas + " que trae la caja");
        }
        LoteFarmacia lote = lote(pieza.loteId);
        sumarPieza(caja.id, lote.getId(), -piezas);
        inventario.mover(lote, MOVIMIENTO_CAJA, piezas, caja.id, usuario);
        evento(caja.id, "SACADO", clave + " lote " + lote.getNumeroLote() + " (" + piezas + ") al estante · " + motivo, usuario);
        return detalle(caja.codigo);
    }

    // ---------- Entregar ----------

    @Transactional
    public CajaResponse entregar(String codigo, EntregarRequest request, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        if (valeAbierto(caja.id) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja " + caja.codigo + " ya está en quirófano");
        }
        String turno = request.turno() == null ? null : request.turno().trim().toUpperCase(Locale.ROOT);
        if (!TURNOS.contains(turno)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el turno: matutino, vespertino, nocturno o fin de semana y festivos");
        }
        String sala = obligatorio(request.sala(), "Escribe la sala o quirófano");
        String medico = obligatorio(request.medico(), "Escribe el médico anestesiólogo que recibe la caja");
        CajaResponse actual = detalle(caja.codigo);
        if (actual.piezasFaltantes() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A la caja le faltan " + actual.piezasFaltantes() + " piezas; repónla antes de entregarla");
        }
        LocalDate hoy = LocalDate.now();
        for (Pieza pieza : piezas(caja.id)) {
            if (pieza.caducidad.isBefore(hoy)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote " + pieza.lote + " de " + pieza.clave + " venció el "
                        + pieza.caducidad + ": sácalo de la caja y repón con otro");
            }
        }
        // El médico, de la lista de Intelisis si se eligió de ahí.
        String medicoUsuario = ServicioInventarioFarmacia.texto(request.medicoUsuario());
        if (medicoUsuario != null) {
            List<String> nombre = haero.queryForList("SELECT nombre FROM hosp_medico WHERE usuario = ?", String.class, medicoUsuario);
            if (nombre.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese médico no está en la lista; escribe su nombre");
            }
            medico = nombre.get(0) != null ? nombre.get(0).trim() : medico;
        }
        // Lo de red fría sale ahora del refrigerador (estante de Farmacia) con la caja.
        List<String> deRedFria = new ArrayList<>();
        for (RenglonCaja renglon : actual.renglones()) {
            if (renglon.aparte()) {
                deRedFria.addAll(tomarDelEstante(caja.id, renglon.clave(), null, renglon.cantidad(), usuario));
            }
        }
        Long valeId = haero.queryForObject("""
                INSERT INTO quirofano_vale (caja_id, turno, sala, medico, medico_usuario, entregado_en, entregado_por)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Long.class, caja.id, turno, sala, medico, medicoUsuario, ahora(), usuario.getId());
        // Columna "Surtido" del vale: lo que sale ahora en la caja (con lo de red fría).
        int orden = 0;
        for (RenglonCaja renglon : actual.renglones()) {
            haero.update("INSERT INTO quirofano_vale_partida (vale_id, medicamento_clave, surtido, orden) VALUES (?, ?, ?, ?)",
                    valeId, renglon.clave(), renglon.aparte() ? renglon.cantidad() : renglon.tiene(), orden++);
        }
        evento(caja.id, "ENTREGA", "A " + medico + " · " + sala + " · turno " + turno.toLowerCase(Locale.ROOT).replace('_', ' ')
                + (deRedFria.isEmpty() ? "" : " · red fría aparte: " + String.join(", ", deRedFria)), usuario);
        return detalle(caja.codigo);
    }

    // ---------- Recibir ----------

    @Transactional
    public CajaResponse recibir(String codigo, RecibirRequest request, List<MultipartFile> documentos, String nombreUsuario) {
        Usuario usuario = inventario.usuario(nombreUsuario);
        Caja caja = caja(codigo, true);
        Long valeId = valeAbierto(caja.id);
        if (valeId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja " + caja.codigo + " no está en quirófano");
        }
        List<UsoRequest> usos = request == null || request.usos() == null ? List.of() : request.usos();
        Map<String, Contenido> contenido = contenido(caja.id);
        List<Pieza> piezas = piezas(caja.id);

        // Validación: cada uso con paciente, clave del listado y piezas que sí traía la caja.
        Map<String, Integer> usadasPorClave = new LinkedHashMap<>();
        Map<String, Paciente> pacientes = new LinkedHashMap<>();
        for (UsoRequest uso : usos) {
            String expediente = obligatorio(uso.expediente(), "Cada medicamento utilizado necesita el expediente del paciente");
            String nombre = obligatorio(uso.nombre(), "Falta el nombre del paciente del expediente " + expediente);
            String clave = obligatorio(uso.clave(), "Falta la clave de un medicamento utilizado");
            if (!contenido.containsKey(clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + clave + " no está en la caja");
            }
            if (uso.piezas() == null || uso.piezas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las piezas utilizadas de " + clave + " deben ser mayores a cero");
            }
            if (expediente.length() > 30 || nombre.length() > 250) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El expediente o el nombre del paciente es demasiado largo");
            }
            usadasPorClave.merge(clave, uso.piezas(), Integer::sum);
            Paciente paciente = pacientes.computeIfAbsent(expediente.toUpperCase(Locale.ROOT), k -> new Paciente(expediente, nombre));
            paciente.piezas.merge(clave, uso.piezas(), Integer::sum);
        }
        for (Map.Entry<String, Integer> usadas : usadasPorClave.entrySet()) {
            int tiene = piezas.stream().filter(p -> p.clave.equals(usadas.getKey())).mapToInt(p -> p.piezas).sum();
            if (usadas.getValue() > tiene) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se registran " + usadas.getValue() + " piezas utilizadas de "
                        + usadas.getKey() + " y la caja salió con " + tiene);
            }
        }
        String justificacion = ServicioInventarioFarmacia.texto(request == null ? null : request.justificacion());
        // Una indicación médica (PDF) por paciente: "indicaciones" dice de qué expediente es cada archivo.
        Map<String, MultipartFile> pdfPorPaciente = new LinkedHashMap<>();
        if (!usos.isEmpty()) {
            if (justificacion == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe la justificación de lo utilizado");
            }
            if (justificacion.length() > 500) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La justificación no puede pasar de 500 caracteres");
            }
            List<String> expedientes = request.indicaciones() == null ? List.of() : request.indicaciones();
            List<MultipartFile> archivos = documentos == null ? List.of() : documentos;
            if (expedientes.size() != archivos.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada indicación médica debe decir de qué paciente es");
            }
            for (int i = 0; i < archivos.size(); i++) {
                pdfPorPaciente.put(expedientes.get(i).trim().toUpperCase(Locale.ROOT), archivos.get(i));
            }
            for (Map.Entry<String, Paciente> paciente : pacientes.entrySet()) {
                if (!pdfPorPaciente.containsKey(paciente.getKey())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta la indicación médica en PDF de " + paciente.getValue().nombre
                            + " (exp. " + paciente.getValue().expediente + ")");
                }
            }
        }

        // Cada paciente es una entrega de Farmacia; las piezas salen de los lotes de la caja (FEFO).
        int n = 0;
        for (Paciente paciente : pacientes.values()) {
            n++;
            Receta receta = new Receta();
            receta.setFolio(PREFIJO_FOLIO + "V" + valeId + "-" + n);
            receta.setPacienteNombre(paciente.nombre);
            receta.setPacienteExpediente(paciente.expediente);
            receta.setEstado(Receta.SURTIDA);
            receta.setUsuario(usuario);
            Entrega entrega = new Entrega();
            entrega.setReceta(receta);
            entrega.setUsuario(usuario);
            for (Map.Entry<String, Integer> usada : paciente.piezas.entrySet()) {
                ClaveReceta claveReceta = new ClaveReceta();
                claveReceta.setReceta(receta);
                claveReceta.setMedicamento(inventario.medicamento(usada.getKey()));
                claveReceta.setPiezasIndicadas(usada.getValue());
                claveReceta.setPiezasEntregadas(usada.getValue());
                receta.getClaves().add(claveReceta);
                int faltan = usada.getValue();
                for (Pieza pieza : piezas.stream().filter(p -> p.clave.equals(usada.getKey()) && p.piezas > 0)
                        .sorted(Comparator.comparing((Pieza p) -> p.caducidad).thenComparing(p -> p.loteId)).toList()) {
                    if (faltan == 0) {
                        break;
                    }
                    int tomadas = Math.min(faltan, pieza.piezas);
                    pieza.piezas -= tomadas;
                    faltan -= tomadas;
                    PartidaEntrega partida = new PartidaEntrega();
                    partida.setEntrega(entrega);
                    partida.setClaveReceta(claveReceta);
                    partida.setLote(lote(pieza.loteId));
                    partida.setPiezas(tomadas);
                    entrega.getPartidas().add(partida);
                }
            }
            recetaRepository.save(receta);
            entregaRepository.saveAndFlush(entrega);
            for (PartidaEntrega partida : entrega.getPartidas()) {
                // Sale de la caja (regresa un momento al lote) y se entrega al paciente: así la
                // bitácora del lote cuadra y la entrega es igual a las de ventanilla.
                inventario.mover(partida.getLote(), MOVIMIENTO_CAJA, partida.getPiezas(), caja.id, usuario);
                inventario.mover(partida.getLote(), MovimientoFarmacia.ENTREGA, -partida.getPiezas(), partida.getId(), usuario);
                sumarPieza(caja.id, partida.getLote().getId(), -partida.getPiezas());
            }
            for (Map.Entry<String, Integer> usada : paciente.piezas.entrySet()) {
                haero.update("""
                        INSERT INTO quirofano_vale_uso (vale_id, paciente_expediente, paciente_nombre, medicamento_clave, piezas, entrega_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, valeId, paciente.expediente, paciente.nombre, usada.getKey(), usada.getValue(), entrega.getId());
            }
        }

        for (Paciente paciente : pacientes.values()) {
            MultipartFile archivo = pdfPorPaciente.get(paciente.expediente.toUpperCase(Locale.ROOT));
            String nombrePdf = archivo.getOriginalFilename() == null || archivo.getOriginalFilename().isBlank()
                    ? "indicacion-" + paciente.expediente + ".pdf" : archivo.getOriginalFilename();
            if (nombrePdf.length() > 255) {
                nombrePdf = nombrePdf.substring(nombrePdf.length() - 255);
            }
            haero.update("INSERT INTO quirofano_vale_indicacion (vale_id, paciente_expediente, documento_nombre, documento) VALUES (?, ?, ?, ?)",
                    valeId, paciente.expediente, nombrePdf, leerPdf(archivo));
        }

        // Lo de red fría que no se usó regresa al refrigerador (estante de Farmacia): no se queda en la caja.
        List<String> regresaron = new ArrayList<>();
        for (Contenido c : contenido.values()) {
            if (!c.aparte) {
                continue;
            }
            for (Pieza pieza : piezas(caja.id).stream().filter(p -> p.clave.equals(c.clave)).toList()) {
                LoteFarmacia lote = lote(pieza.loteId);
                sumarPieza(caja.id, lote.getId(), -pieza.piezas);
                inventario.mover(lote, MOVIMIENTO_CAJA, pieza.piezas, caja.id, usuario);
                regresaron.add(c.clave + " lote " + lote.getNumeroLote() + " (" + pieza.piezas + ")");
            }
        }

        haero.update("UPDATE quirofano_vale SET recibido_en = ?, recibido_por = ?, justificacion = ? WHERE id = ?",
                ahora(), usuario.getId(), justificacion, valeId);
        int total = usadasPorClave.values().stream().mapToInt(Integer::intValue).sum();
        evento(caja.id, "DEVOLUCION", (total == 0 ? "Regresó completa"
                : "Utilizado: " + total + " piezas en " + pacientes.size() + " paciente(s) · " + justificacion)
                + (regresaron.isEmpty() ? "" : " · red fría al refrigerador: " + String.join(", ", regresaron)), usuario);
        return detalle(caja.codigo);
    }

    // ---------- comunes ----------

    private record Caja(Long id, String codigo, String nombre, String servicio) {
    }

    /** nombre = el del catálogo; descripcion = el que escribió Farmacia (o null). */
    private record Contenido(String clave, String nombre, int cantidad, boolean aparte, String descripcion) {
        String mostrado() {
            return descripcion != null ? descripcion : nombre;
        }
    }

    private static final class Pieza {
        private final long loteId;
        private final String clave;
        private final String lote;
        private final LocalDate caducidad;
        private int piezas;

        private Pieza(long loteId, String clave, String lote, LocalDate caducidad, int piezas) {
            this.loteId = loteId;
            this.clave = clave;
            this.lote = lote;
            this.caducidad = caducidad;
            this.piezas = piezas;
        }
    }

    private static final class Paciente {
        private final String expediente;
        private final String nombre;
        private final Map<String, Integer> piezas = new LinkedHashMap<>();

        private Paciente(String expediente, String nombre) {
            this.expediente = expediente;
            this.nombre = nombre;
        }
    }

    /** La caja por su código. "bloquear" la aparta para que dos personas no la muevan a la vez. */
    private Caja caja(String codigo, boolean bloquear) {
        String limpio = ServicioInventarioFarmacia.texto(codigo);
        if (limpio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escanea el código de la caja");
        }
        String sql = "SELECT id, codigo, nombre, servicio FROM quirofano_caja " + (bloquear ? "WITH (UPDLOCK, ROWLOCK) " : "")
                + "WHERE codigo = ? AND activa = 1";
        return haero.query(sql, (rs, n) -> new Caja(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                        limpio.toUpperCase(Locale.ROOT))
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una caja con el código " + limpio));
    }

    private boolean existeCodigo(String codigo) {
        Integer total = haero.queryForObject("SELECT COUNT(*) FROM quirofano_caja WHERE codigo = ?", Integer.class, codigo);
        return total != null && total > 0;
    }

    private Map<String, Contenido> contenido(Long cajaId) {
        Map<String, Contenido> contenido = new LinkedHashMap<>();
        haero.query("""
                SELECT c.medicamento_clave, m.nombre_generico, c.cantidad, c.aparte, c.descripcion FROM quirofano_caja_contenido c
                JOIN medicamento m ON m.clave = c.medicamento_clave WHERE c.caja_id = ? ORDER BY c.orden
                """, rs -> {
            contenido.put(rs.getString(1), new Contenido(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getString(5)));
        }, cajaId);
        return contenido;
    }

    private List<Pieza> piezas(Long cajaId) {
        return haero.query("""
                SELECT p.farmacia_lote_id, f.medicamento_clave, f.numero_lote, f.caducidad, p.piezas
                FROM quirofano_caja_pieza p JOIN farmacia_lote f ON f.id = p.farmacia_lote_id
                WHERE p.caja_id = ? AND p.piezas > 0 ORDER BY f.medicamento_clave, f.caducidad, f.id
                """, (rs, n) -> new Pieza(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate(), rs.getInt(5)), cajaId);
    }

    private void sumarPieza(Long cajaId, Long loteId, int piezas) {
        if (haero.update("UPDATE quirofano_caja_pieza SET piezas = piezas + ? WHERE caja_id = ? AND farmacia_lote_id = ?",
                piezas, cajaId, loteId) == 0) {
            haero.update("INSERT INTO quirofano_caja_pieza (caja_id, farmacia_lote_id, piezas) VALUES (?, ?, ?)", cajaId, loteId, piezas);
        }
    }

    private Long valeAbierto(Long cajaId) {
        return haero.queryForList("SELECT id FROM quirofano_vale WHERE caja_id = ? AND recibido_en IS NULL", Long.class, cajaId)
                .stream().findFirst().orElse(null);
    }

    private List<ValeResponse> vales(Long cajaId) {
        List<Map<String, Object>> filas = haero.queryForList("""
                SELECT TOP (?) v.id, v.turno, v.sala, v.medico, v.entregado_en, ue.nombre_completo AS entrego,
                       v.recibido_en, ur.nombre_completo AS recibio, v.justificacion
                FROM quirofano_vale v
                JOIN usuario ue ON ue.id = v.entregado_por
                LEFT JOIN usuario ur ON ur.id = v.recibido_por
                WHERE v.caja_id = ? ORDER BY v.entregado_en DESC, v.id DESC
                """, VALES_EN_RESPUESTA, cajaId);
        List<ValeResponse> vales = new ArrayList<>();
        for (Map<String, Object> fila : filas) {
            long id = ((Number) fila.get("id")).longValue();
            List<UsoResponse> usos = haero.query("""
                    SELECT paciente_expediente, paciente_nombre, medicamento_clave, piezas, entrega_id FROM quirofano_vale_uso
                    WHERE vale_id = ? ORDER BY id
                    """, (rs, n) -> {
                long entregaId = rs.getLong(5);
                return new UsoResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.wasNull() ? null : Entrega.folio(entregaId));
            }, id);
            Map<String, Integer> utilizado = new LinkedHashMap<>();
            usos.forEach(u -> utilizado.merge(u.clave(), u.piezas(), Integer::sum));
            // En el vale sale el nombre que Farmacia le puso en la caja (si lo reescribió).
            List<PartidaVale> partidas = haero.query("""
                    SELECT p.medicamento_clave, COALESCE(cc.descripcion, m.nombre_generico), p.surtido FROM quirofano_vale_partida p
                    JOIN medicamento m ON m.clave = p.medicamento_clave
                    JOIN quirofano_vale v ON v.id = p.vale_id
                    LEFT JOIN quirofano_caja_contenido cc ON cc.caja_id = v.caja_id AND cc.medicamento_clave = p.medicamento_clave
                    WHERE p.vale_id = ? ORDER BY p.orden
                    """, (rs, n) -> new PartidaVale(rs.getString(1), rs.getString(2), rs.getInt(3),
                    utilizado.getOrDefault(rs.getString(1), 0)), id);
            List<String> indicaciones = haero.queryForList(
                    "SELECT paciente_expediente FROM quirofano_vale_indicacion WHERE vale_id = ? ORDER BY paciente_expediente", String.class, id);
            vales.add(new ValeResponse(id, (String) fila.get("turno"), (String) fila.get("sala"), (String) fila.get("medico"),
                    fecha(fila.get("entregado_en")), (String) fila.get("entrego"), fecha(fila.get("recibido_en")),
                    (String) fila.get("recibio"), (String) fila.get("justificacion"), indicaciones, usos, partidas));
        }
        return vales;
    }

    private List<Contenido> validarContenido(List<ContenidoRequest> contenido) {
        if (contenido == null || contenido.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un medicamento al listado de la caja");
        }
        List<Contenido> lista = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (ContenidoRequest item : contenido) {
            Medicamento medicamento = inventario.medicamento(item.clave());
            if (!vistas.add(medicamento.getClave())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + medicamento.getClave() + " viene dos veces en el listado");
            }
            if (item.cantidad() == null || item.cantidad() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las piezas de " + medicamento.getClave() + " en la caja");
            }
            // Lo de red fría sí va en el vale, pero no se guarda en la caja: se entrega aparte al momento.
            boolean aparte = Boolean.TRUE.equals(item.aparte()) || esRedFria(medicamento.getClave());
            // Nombre propio: si viene vacío o igual al del catálogo, se usa el del catálogo.
            String descripcion = ServicioInventarioFarmacia.texto(item.descripcion());
            if (descripcion != null && descripcion.length() > 200) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de " + medicamento.getClave() + " no puede pasar de 200 caracteres");
            }
            if (descripcion != null && descripcion.equalsIgnoreCase(ServicioInventarioFarmacia.texto(medicamento.getNombreGenerico()))) {
                descripcion = null;
            }
            lista.add(new Contenido(medicamento.getClave(), medicamento.getNombreGenerico(), item.cantidad(), aparte, descripcion));
        }
        return lista;
    }

    private void guardarContenido(Long cajaId, List<Contenido> contenido) {
        int orden = 0;
        for (Contenido item : contenido) {
            haero.update("INSERT INTO quirofano_caja_contenido (caja_id, medicamento_clave, cantidad, orden, aparte, descripcion) VALUES (?, ?, ?, ?, ?, ?)",
                    cajaId, item.clave, item.cantidad, orden++, item.aparte, item.descripcion);
        }
    }

    private boolean esRedFria(String clave) {
        return haero.queryForList("SELECT red_fria FROM medicamento_seguridad WHERE clave = ?", Boolean.class, clave)
                .stream().anyMatch(Boolean.TRUE::equals);
    }

    private LoteFarmacia lote(long id) {
        return loteFarmaciaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El lote ya no está en Farmacia"));
    }

    private void evento(Long cajaId, String accion, String detalle, Usuario usuario) {
        String texto = detalle != null && detalle.length() > 1000 ? detalle.substring(0, 1000) : detalle;
        haero.update("INSERT INTO quirofano_caja_evento (caja_id, accion, detalle, fecha, usuario_id) VALUES (?, ?, ?, ?, ?)",
                cajaId, accion, texto, ahora(), usuario.getId());
    }

    private static String resumen(List<Contenido> contenido) {
        return String.join(", ", contenido.stream().map(c -> c.clave + " x" + c.cantidad + (c.aparte ? " (red fría, aparte)" : "")).toList());
    }

    private static String obligatorio(String valor, String mensaje) {
        String limpio = ServicioInventarioFarmacia.texto(valor);
        if (limpio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        return limpio;
    }

    // La fecha sale de la aplicación: el SQL Server puede correr en otra zona horaria.
    private static Timestamp ahora() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    private static LocalDateTime fecha(Object valor) {
        return valor instanceof Timestamp t ? t.toLocalDateTime() : null;
    }

    /** PDF de máximo 10 MB que de verdad empieza con "%PDF". */
    static byte[] leerPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta la indicación médica en PDF");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_PDF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PDF no puede pesar más de 10 MB");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo adjunto");
        }
        if (contenido.length < 4 || !Arrays.equals(Arrays.copyOf(contenido, 4), new byte[]{'%', 'P', 'D', 'F'})) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser un PDF");
        }
        return contenido;
    }
}
