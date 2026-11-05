package com.example.backend.farmacia.service;

import com.example.backend.entity.Usuario;
import com.example.backend.intelisis.ConexionIntelisis;
import com.example.backend.farmacia.dto.CajaEntregaRequest;
import com.example.backend.farmacia.dto.ClaveRecetaRequest;
import com.example.backend.farmacia.dto.EntregaResponse;
import com.example.backend.farmacia.dto.PacienteRequest;
import com.example.backend.farmacia.dto.PacienteResponse;
import com.example.backend.farmacia.dto.PartidaEntregadaResponse;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.ClaveSugerida;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.EstadoQuimio;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.MedicamentoEnNota;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.QuimioResponse;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.RegistrarQuimioRequest;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.RenglonQuimioRequest;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.RenglonQuimioResponse;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SesionesSolicitud;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SolicitudQuimio;
import com.example.backend.farmacia.dto.QuimioterapiaDtos.SolicitudQuimioDetalle;
import com.example.backend.farmacia.dto.RegistrarEntregaRequest;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quimioterapia. Se parte de la solicitud de procedimiento (folio y expediente), que a veces trae
 * el expediente de otro paciente: el paciente se confirma en Intelisis y, si el nombre no coincide,
 * se corrige el expediente o se justifica. El médico escribe los medicamentos como nota (sin clave,
 * a veces "si no hay A, dar B"): Farmacia los pasa a clave y surte. Es de alto riesgo: otra persona
 * de Farmacia verifica con SU usuario y contraseña antes de registrar.
 *
 *  - Una solicitud puede cubrir varias sesiones o ciclos: cada surtido es una sesión (1, 2, 3…) y
 *    todas deben ser del mismo paciente.
 *  - La dosis se puede capturar en mg (o la unidad indicada) contra lo que trae cada frasco: HAERO
 *    calcula lo que sobra y Farmacia dice si se desecha (MERMA) o se guarda (RESGUARDO).
 *  - Si se surte la alternativa habiendo existencia de la principal, se pide el motivo.
 *  - Si Intelisis no responde, se confirma al paciente con el expediente físico y queda justificado.
 *
 * Lo surtido es una entrega normal de Farmacia (receta "QUIM-<folio>-S<sesión>"): descuenta el
 * inventario por lote (FEFO), entra a la baja por volumen de Quimioterapia y a la trazabilidad.
 */
@Service
public class ServicioQuimioterapia {

    static final String PREFIJO_FOLIO = "QUIM-";
    private static final String ROL_FARMACIA = "FARMACIA";
    private static final Set<String> DESTINOS_SOBRANTE = Set.of("MERMA", "RESGUARDO");

    // Filtros de la lista de solicitudes de Oncología.
    static final String POR_SURTIR = "POR_SURTIR";
    static final String PARA_HOY = "HOY";
    static final String SURTIDAS = "SURTIDAS";
    private static final int MAXIMO_LISTA = 200;
    private static final int MAXIMO_CLAVES_SUGERIDAS = 6;

    private final JdbcTemplate haero;
    private final ServicioEntregas servicioEntregas;
    private final ServicioPacientesIntelisis pacientesIntelisis;
    private final ServicioInventarioFarmacia inventario;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConexionIntelisis conexion;
    private final ServicioSincronizacionQuimio sincronizacion;

    public ServicioQuimioterapia(JdbcTemplate haero,
                                 ServicioEntregas servicioEntregas,
                                 ServicioPacientesIntelisis pacientesIntelisis,
                                 ServicioInventarioFarmacia inventario,
                                 UsuarioRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder,
                                 ConexionIntelisis conexion,
                                 ServicioSincronizacionQuimio sincronizacion) {
        this.haero = haero;
        this.servicioEntregas = servicioEntregas;
        this.pacientesIntelisis = pacientesIntelisis;
        this.inventario = inventario;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.conexion = conexion;
        this.sincronizacion = sincronizacion;
    }

    // ---------- Solicitudes de Oncología (leídas de Intelisis) ----------

    /**
     * Solicitudes de Oncología Médica de la copia de Intelisis. filtro: POR_SURTIR (sin ninguna sesión
     * surtida y con fecha requerida desde hace 7 días), HOY (requeridas hoy) o SURTIDAS; cualquier otro,
     * todas. buscar: folio de la hoja, expediente o parte del nombre.
     */
    public List<SolicitudQuimio> solicitudes(String filtro, String buscar) {
        List<Object> parametros = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        LocalDate hoy = LocalDate.now();
        if (POR_SURTIR.equals(filtro)) {
            where.append(" AND x.sesiones = 0 AND (x.fecha_requerida IS NULL OR x.fecha_requerida >= ?)");
            parametros.add(Timestamp.valueOf(hoy.minusDays(7).atStartOfDay()));
        } else if (PARA_HOY.equals(filtro)) {
            where.append(" AND x.fecha_requerida >= ? AND x.fecha_requerida < ?");
            parametros.add(Timestamp.valueOf(hoy.atStartOfDay()));
            parametros.add(Timestamp.valueOf(hoy.plusDays(1).atStartOfDay()));
        } else if (SURTIDAS.equals(filtro)) {
            where.append(" AND x.sesiones > 0");
        }
        String texto = ServicioInventarioFarmacia.texto(buscar);
        if (texto != null) {
            where.append(" AND (CAST(x.folio AS VARCHAR(12)) = ? OR x.expediente = ? OR x.paciente LIKE ?)");
            parametros.add(texto);
            parametros.add(texto);
            parametros.add("%" + texto.replace("%", "").replace("_", "") + "%");
        }
        // Las de hoy y las próximas primero; luego las que ya pasaron, de la más reciente a la más vieja.
        String sql = "SELECT TOP (" + MAXIMO_LISTA + ") x.folio, x.fecha, x.fecha_requerida, x.estatus, x.expediente, x.paciente, "
                + "x.servicio_solicita, x.medico, x.sesiones, x.ultima FROM (" + SOLICITUDES_CON_SESIONES + ") x " + where
                + " ORDER BY CASE WHEN x.fecha_requerida >= ? THEN 0 ELSE 1 END, "
                + "CASE WHEN x.fecha_requerida >= ? THEN x.fecha_requerida END ASC, x.fecha_requerida DESC, x.folio DESC";
        parametros.add(Timestamp.valueOf(hoy.atStartOfDay()));
        parametros.add(Timestamp.valueOf(hoy.atStartOfDay()));
        return haero.query(sql, (rs, n) -> solicitud(rs), parametros.toArray());
    }

    /**
     * Una solicitud con su nota pasada a sugerencias de clave y lo que se surtió al paciente la vez
     * anterior. Si el folio ya salió de la copia (hoja vieja), se lee de Intelisis en ese momento.
     */
    public SolicitudQuimioDetalle solicitud(String folio, String nombreUsuario) {
        int numero = numeroDeFolio(folio);
        Usuario usuario = inventario.usuario(nombreUsuario);
        List<SolicitudQuimioDetalle> copia = haero.query("SELECT x.folio, x.fecha, x.fecha_requerida, x.estatus, x.expediente, x.paciente, "
                        + "x.servicio_solicita, x.medico, x.sesiones, x.ultima, x.diagnostico, x.notas FROM (" + SOLICITUDES_CON_SESIONES
                        + ") x WHERE x.folio = ?",
                (rs, n) -> new SolicitudQuimioDetalle(solicitud(rs), rs.getString(11), rs.getString(12), null, null), numero);
        SolicitudQuimioDetalle encontrada = copia.isEmpty() ? deIntelisis(numero) : copia.get(0);
        // Quién vio los datos de qué paciente.
        haero.update("INSERT INTO quimio_consulta (usuario_id, folio, fecha) VALUES (?, ?, ?)",
                usuario.getId(), numero, Timestamp.valueOf(LocalDateTime.now()));

        QuimioResponse anterior = cicloAnterior(encontrada.solicitud().expediente());
        Set<String> usadasAntes = new HashSet<>();
        if (anterior != null) {
            anterior.renglones().forEach(r -> {
                usadasAntes.add(r.claveIndicada());
                if (r.claveAlternativa() != null) usadasAntes.add(r.claveAlternativa());
            });
        }
        return new SolicitudQuimioDetalle(encontrada.solicitud(), encontrada.diagnostico(), encontrada.notas(),
                medicamentosEnNota(encontrada.notas(), usadasAntes), anterior);
    }

    public EstadoQuimio estado() {
        List<Map<String, Object>> ultima = haero.queryForList(
                "SELECT TOP 1 estatus, error FROM quimio_sincronizacion WHERE estatus <> 'EN_CURSO' ORDER BY id DESC");
        List<Timestamp> fin = haero.queryForList("SELECT TOP 1 fin FROM quimio_sincronizacion WHERE estatus = 'OK' ORDER BY id DESC", Timestamp.class);
        Integer total = haero.queryForObject("SELECT COUNT(*) FROM quimio_solicitud", Integer.class);
        return new EstadoQuimio(conexion.disponible(), sincronizacion.estaEnCurso(),
                fin.isEmpty() || fin.get(0) == null ? null : fin.get(0).toLocalDateTime(),
                ultima.isEmpty() ? null : (String) ultima.get(0).get("estatus"),
                ultima.isEmpty() ? null : (String) ultima.get(0).get("error"), total == null ? 0 : total);
    }

    /** La solicitud con cuántas sesiones se han surtido de ella en HAERO. */
    private static final String SOLICITUDES_CON_SESIONES = """
            SELECT s.*, COALESCE(p.sesiones, 0) AS sesiones, p.ultima
            FROM quimio_solicitud s
            LEFT JOIN (SELECT folio_solicitud, COUNT(*) AS sesiones, MAX(fecha) AS ultima FROM quimio_preparacion GROUP BY folio_solicitud) p
                   ON p.folio_solicitud = CAST(s.folio AS VARCHAR(30))
            """;

    private static SolicitudQuimio solicitud(ResultSet rs) throws SQLException {
        return new SolicitudQuimio(rs.getInt(1), fecha(rs.getTimestamp(2)), fecha(rs.getTimestamp(3)), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9), fecha(rs.getTimestamp(10)));
    }

    /** Hoja con un folio que ya no está en la copia: se lee solo esa solicitud de Intelisis. */
    private SolicitudQuimioDetalle deIntelisis(int folio) {
        JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No hay conexión con Intelisis para leer la solicitud " + folio + ". " + conexion.motivoNoDisponible()));
        List<Object[]> filas;
        try {
            filas = intelisis.query(ServicioSincronizacionQuimio.SELECT_SOLICITUD + " AND s.ID_AuxTxSol = ?",
                    (rs, n) -> ServicioSincronizacionQuimio.fila(rs), folio);
        } catch (DataAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Intelisis no respondió; intenta de nuevo en un momento");
        }
        if (filas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una solicitud de Oncología Médica con el folio " + folio
                    + " en Intelisis (o está cancelada)");
        }
        Object[] f = filas.get(0);
        SesionesSolicitud previas = sesiones(String.valueOf(folio));
        LocalDateTime ultima = previas.sesiones().isEmpty() ? null : previas.sesiones().get(previas.sesiones().size() - 1).fecha();
        SolicitudQuimio s = new SolicitudQuimio(folio, fecha((Timestamp) f[1]), fecha((Timestamp) f[2]), (String) f[3], (String) f[4],
                (String) f[5], (String) f[6], (String) f[10], previas.sesiones().size(), ultima);
        return new SolicitudQuimioDetalle(s, (String) f[7], (String) f[8], null, null);
    }

    /** Lo último que se le surtió de quimioterapia al paciente (de cualquier solicitud). */
    private QuimioResponse cicloAnterior(String expediente) {
        if (expediente == null) {
            return null;
        }
        List<Long> ids = haero.queryForList("SELECT TOP 1 id FROM quimio_preparacion WHERE paciente_expediente = ? ORDER BY fecha DESC",
                Long.class, expediente);
        return ids.isEmpty() ? null : detalle(ids.get(0), null);
    }

    /** Los medicamentos de la nota con sus claves posibles: primero las que ya se usaron con el paciente y las que hay en Farmacia. */
    private List<MedicamentoEnNota> medicamentosEnNota(String notas, Set<String> usadasAntes) {
        if (notas == null || notas.isBlank()) {
            return List.of();
        }
        Map<String, Object[]> catalogo = new LinkedHashMap<>();
        haero.query("SELECT clave, nombre_generico, forma_farmaceutica, concentracion FROM medicamento WHERE estatus IS NULL OR estatus <> 'BAJA'",
                rs -> {
                    catalogo.put(rs.getString(1), new Object[]{rs.getString(2), rs.getString(3), rs.getString(4)});
                });
        Map<String, Integer> existencias = new HashMap<>();
        haero.query("SELECT medicamento_clave, SUM(piezas) FROM farmacia_lote WHERE piezas > 0 AND caducidad >= ? GROUP BY medicamento_clave",
                rs -> {
                    existencias.put(rs.getString(1), rs.getInt(2));
                }, java.sql.Date.valueOf(LocalDate.now()));
        List<LectorNotaQuimio.Farmaco> farmacos = catalogo.entrySet().stream()
                .map(e -> new LectorNotaQuimio.Farmaco(e.getKey(), LectorNotaQuimio.palabrasFarmaco((String) e.getValue()[0])))
                .toList();

        List<MedicamentoEnNota> resultado = new ArrayList<>();
        for (LectorNotaQuimio.Hallazgo h : LectorNotaQuimio.leer(notas, farmacos)) {
            List<ClaveSugerida> claves = h.claves().stream().map(clave -> {
                Object[] m = catalogo.get(clave);
                BigDecimal[] contenido = new BigDecimal[1];
                String unidad = LectorNotaQuimio.contenido((String) m[2], contenido);
                return new ClaveSugerida(clave, (String) m[0], (String) m[1], (String) m[2], contenido[0], unidad,
                        existencias.getOrDefault(clave, 0), usadasAntes.contains(clave));
            }).sorted(Comparator.comparing((ClaveSugerida c) -> !c.usadaAntes())
                    .thenComparing(c -> c.existencia() <= 0)
                    .thenComparing(c -> c.concentracion() == null)
                    .thenComparing(ClaveSugerida::clave))
                    .limit(MAXIMO_CLAVES_SUGERIDAS).toList();
            resultado.add(new MedicamentoEnNota(h.texto(), h.farmaco(), h.dosisCantidad(), h.dosisUnidad(), claves));
        }
        return resultado;
    }

    private static int numeroDeFolio(String folio) {
        String limpio = obligatorio(folio, "Escribe el folio de la solicitud");
        if (!limpio.matches("\\d{1,9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El folio de la solicitud es un número (el que trae la hoja impresa)");
        }
        return Integer.parseInt(limpio);
    }

    private static LocalDateTime fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    /** Lo que ya se surtió de una solicitud (vacío si es la primera vez). */
    public SesionesSolicitud sesiones(String folio) {
        String limpio = obligatorio(folio, "Escribe el folio de la solicitud");
        List<Long> ids = haero.queryForList("SELECT id FROM quimio_preparacion WHERE folio_solicitud = ? ORDER BY sesion", Long.class, limpio);
        List<QuimioResponse> sesiones = ids.stream().map(id -> detalle(id, null)).toList();
        QuimioResponse primera = sesiones.isEmpty() ? null : sesiones.get(0);
        return new SesionesSolicitud(limpio, primera == null ? null : primera.pacienteExpediente(),
                primera == null ? null : primera.pacienteNombre(), sesiones);
    }

    @Transactional
    public QuimioResponse registrar(RegistrarQuimioRequest request, String nombreUsuario) {
        Usuario surtio = inventario.usuario(nombreUsuario);
        String folio = obligatorio(request.folio(), "Escribe el folio de la solicitud de procedimiento");
        if (folio.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El folio no puede pasar de 30 caracteres");
        }
        String expedienteSolicitud = obligatorio(request.expedienteSolicitud(), "Escribe el expediente que trae la solicitud");

        // 1. El paciente se confirma en Intelisis; se guarda el nombre como está allá. Si Intelisis no
        //    responde, Farmacia lo confirma con el expediente físico y lo justifica.
        if (request.paciente() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verifica al paciente en Intelisis");
        }
        String expediente = obligatorio(request.paciente().expediente(), "Verifica al paciente en Intelisis");
        String justificacionSinIntelisis = ServicioInventarioFarmacia.texto(request.justificacionSinIntelisis());
        boolean sinIntelisis = false;
        PacienteResponse paciente;
        try {
            paciente = pacientesIntelisis.buscar(expediente, null, null).stream()
                    .filter(p -> expediente.equalsIgnoreCase(p.getExpediente())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El expediente " + expediente + " no existe en Intelisis"));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw e;
            }
            if (justificacionSinIntelisis == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Intelisis no responde. Confirma al paciente con el expediente "
                        + "físico y escribe cómo lo confirmaste para registrar sin Intelisis");
            }
            String nombre = obligatorio(request.paciente().nombre(), "Escribe el nombre del paciente como viene en el expediente físico");
            paciente = new PacienteResponse(nombre, expediente, ServicioInventarioFarmacia.texto(request.paciente().curp()));
            sinIntelisis = true;
        }
        boolean corregido = !expediente.equalsIgnoreCase(expedienteSolicitud);
        String nombreSolicitud = ServicioInventarioFarmacia.texto(request.nombreSolicitud());
        String justificacion = ServicioInventarioFarmacia.texto(request.justificacionPaciente());
        if (!corregido && nombreSolicitud != null && !nombresCoinciden(nombreSolicitud, paciente.getNombre()) && justificacion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de la solicitud (" + nombreSolicitud
                    + ") no coincide con el del expediente (" + paciente.getNombre() + "). Busca al paciente correcto o justifica");
        }
        if (sinIntelisis) {
            justificacion = "Sin Intelisis: " + justificacionSinIntelisis + (justificacion != null ? " · " + justificacion : "");
        }
        if (justificacion != null && justificacion.length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La justificación no puede pasar de 300 caracteres");
        }

        // 2. Sesión: una solicitud puede surtirse varias veces (ciclos), siempre al mismo paciente.
        SesionesSolicitud anteriores = sesiones(folio);
        if (anteriores.pacienteExpediente() != null && !anteriores.pacienteExpediente().equalsIgnoreCase(paciente.getExpediente())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud " + folio + " ya se surtió al expediente "
                    + anteriores.pacienteExpediente() + " (" + anteriores.pacienteNombre() + "); no puede ser de otro paciente");
        }
        int sesion = anteriores.sesiones().size() + 1;

        // 3. La segunda persona verifica con su propio usuario.
        Usuario verifico = verificador(request.verificadorUsuario(), request.verificadorContrasena(), surtio);

        // 4. Lo indicado ya en clave; se surte la principal o la alternativa.
        List<RenglonQuimioRequest> renglones = request.renglones() == null ? List.of() : request.renglones();
        if (renglones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pasa a clave al menos un medicamento de la indicación");
        }
        Map<String, Integer> piezasPorClave = new LinkedHashMap<>();
        List<CajaEntregaRequest> cajas = new ArrayList<>();
        List<Object[]> filas = new ArrayList<>();
        for (RenglonQuimioRequest renglon : renglones) {
            String indicada = inventario.medicamento(renglon.clave()).getClave();
            String alternativa = ServicioInventarioFarmacia.texto(renglon.alternativa()) == null ? null
                    : inventario.medicamento(renglon.alternativa()).getClave();
            if (renglon.usarAlternativa() && alternativa == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + indicada + " no tiene alternativa indicada");
            }
            String surtida = renglon.usarAlternativa() ? alternativa : indicada;
            String dosis = obligatorio(renglon.dosis(), "Escribe la dosis indicada de " + indicada);
            if (dosis.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La dosis de " + indicada + " no puede pasar de 100 caracteres");
            }
            if (renglon.piezas() == null || renglon.piezas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica las piezas a surtir de " + surtida);
            }
            // Alternativa con existencia de la principal: debe decir por qué.
            String motivoAlternativa = ServicioInventarioFarmacia.texto(renglon.motivoAlternativa());
            if (renglon.usarAlternativa() && motivoAlternativa == null && existencia(indicada) >= renglon.piezas()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay existencia de " + indicada
                        + ": escribe por qué se surte la alternativa " + alternativa);
            }
            if (motivoAlternativa != null && motivoAlternativa.length() > 300) {
                motivoAlternativa = motivoAlternativa.substring(0, 300);
            }
            // Dosis contra lo que trae cada frasco: lo que sobra y qué se hace con ello.
            BigDecimal cantidad = renglon.dosisCantidad();
            BigDecimal porPieza = renglon.contenidoPorPieza();
            String unidad = ServicioInventarioFarmacia.texto(renglon.dosisUnidad());
            String destino = renglon.destinoSobrante() == null ? null : renglon.destinoSobrante().trim().toUpperCase(Locale.ROOT);
            if (cantidad != null && porPieza != null) {
                if (cantidad.signum() <= 0 || porPieza.signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La dosis y el contenido por frasco de " + surtida + " deben ser mayores a cero");
                }
                BigDecimal disponible = porPieza.multiply(BigDecimal.valueOf(renglon.piezas()));
                if (disponible.compareTo(cantidad) < 0) {
                    int minimo = cantidad.divide(porPieza, 0, RoundingMode.CEILING).intValue();
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Con " + renglon.piezas() + " piezas de " + surtida
                            + " no alcanza la dosis de " + cantidad.stripTrailingZeros().toPlainString() + " " + (unidad == null ? "" : unidad)
                            + ": se necesitan al menos " + minimo);
                }
                if (disponible.compareTo(cantidad) > 0 && !DESTINOS_SOBRANTE.contains(destino)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sobra parte de un frasco de " + surtida
                            + ": indica si se desecha (merma) o se guarda (resguardo)");
                }
                if (disponible.compareTo(cantidad) == 0) {
                    destino = null;
                }
            } else {
                destino = null;
            }
            if (unidad != null && unidad.length() > 10) {
                unidad = unidad.substring(0, 10);
            }
            String texto = ServicioInventarioFarmacia.texto(renglon.textoMedico());
            if (texto != null && texto.length() > 300) {
                texto = texto.substring(0, 300);
            }
            piezasPorClave.merge(surtida, renglon.piezas(), Integer::sum);
            CajaEntregaRequest caja = new CajaEntregaRequest();
            caja.setClave(surtida);
            caja.setLote(ServicioInventarioFarmacia.texto(renglon.lote()));
            caja.setPiezas(renglon.piezas());
            cajas.add(caja);
            filas.add(new Object[]{texto, indicada, alternativa, surtida, dosis, renglon.piezas(), motivoAlternativa,
                    cantidad, unidad, porPieza, destino});
        }

        RegistrarEntregaRequest entrega = new RegistrarEntregaRequest();
        entrega.setFolioReceta(PREFIJO_FOLIO + folio + "-S" + sesion);
        PacienteRequest datosPaciente = new PacienteRequest();
        datosPaciente.setNombre(paciente.getNombre());
        datosPaciente.setExpediente(paciente.getExpediente());
        datosPaciente.setCurp(paciente.getCurp());
        entrega.setPaciente(datosPaciente);
        List<ClaveRecetaRequest> claves = new ArrayList<>();
        piezasPorClave.forEach((clave, piezas) -> {
            ClaveRecetaRequest c = new ClaveRecetaRequest();
            c.setClave(clave);
            c.setPiezasIndicadas(piezas);
            claves.add(c);
        });
        entrega.setClaves(claves);
        entrega.setCajas(cajas);
        EntregaResponse registrada = servicioEntregas.registrar(entrega, nombreUsuario);

        String nota = ServicioInventarioFarmacia.texto(request.notaMedico());
        if (nota != null && nota.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nota del médico no puede pasar de 2000 caracteres");
        }
        Long id = haero.queryForObject("""
                INSERT INTO quimio_preparacion (folio_solicitud, sesion, expediente_solicitud, nombre_solicitud, paciente_expediente, paciente_nombre,
                    paciente_curp, expediente_corregido, justificacion_paciente, sin_intelisis, nota_medico, entrega_id, surtio_id, verifico_id, fecha)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, folio, sesion, expedienteSolicitud, nombreSolicitud, paciente.getExpediente(), paciente.getNombre(),
                paciente.getCurp(), corregido, justificacion, sinIntelisis, nota, registrada.getId(), surtio.getId(), verifico.getId(),
                Timestamp.valueOf(LocalDateTime.now()));
        for (Object[] fila : filas) {
            haero.update("""
                    INSERT INTO quimio_renglon (preparacion_id, texto_medico, clave_indicada, clave_alternativa, clave_surtida, dosis, piezas,
                        motivo_alternativa, dosis_cantidad, dosis_unidad, contenido_por_pieza, destino_sobrante)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, fila[0], fila[1], fila[2], fila[3], fila[4], fila[5], fila[6], fila[7], fila[8], fila[9], fila[10]);
        }
        return detalle(id, registrada.getPartidas());
    }

    public List<QuimioResponse> recientes() {
        List<Long> ids = haero.queryForList("SELECT TOP 30 id FROM quimio_preparacion ORDER BY fecha DESC", Long.class);
        return ids.stream().map(id -> detalle(id, null)).toList();
    }

    /** Piezas vigentes de la clave en el estante de Farmacia. */
    private int existencia(String clave) {
        Integer piezas = haero.queryForObject("SELECT COALESCE(SUM(piezas), 0) FROM farmacia_lote WHERE medicamento_clave = ? AND caducidad >= ?",
                Integer.class, clave, java.sql.Date.valueOf(LocalDate.now()));
        return piezas == null ? 0 : piezas;
    }

    private QuimioResponse detalle(Long id, List<PartidaEntregadaResponse> partidas) {
        List<RenglonQuimioResponse> renglones = haero.query("""
                SELECT r.texto_medico, r.clave_indicada, r.clave_alternativa, r.clave_surtida, m.nombre_generico, r.dosis, r.piezas,
                       r.motivo_alternativa, r.dosis_cantidad, r.dosis_unidad, r.contenido_por_pieza, r.destino_sobrante,
                       mi.nombre_generico, ma.nombre_generico
                FROM quimio_renglon r JOIN medicamento m ON m.clave = r.clave_surtida
                JOIN medicamento mi ON mi.clave = r.clave_indicada
                LEFT JOIN medicamento ma ON ma.clave = r.clave_alternativa
                WHERE r.preparacion_id = ? ORDER BY r.id
                """, (rs, n) -> {
            BigDecimal cantidad = rs.getBigDecimal(9);
            BigDecimal porPieza = rs.getBigDecimal(11);
            BigDecimal sobrante = cantidad != null && porPieza != null
                    ? porPieza.multiply(BigDecimal.valueOf(rs.getInt(7))).subtract(cantidad).stripTrailingZeros() : null;
            return new RenglonQuimioResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getInt(7), rs.getString(8), cantidad, rs.getString(10), porPieza, sobrante, rs.getString(12),
                    rs.getString(13), rs.getString(14));
        }, id);
        return haero.query("""
                SELECT p.id, p.folio_solicitud, p.sesion, p.expediente_solicitud, p.paciente_nombre, p.paciente_expediente, p.expediente_corregido,
                       p.justificacion_paciente, p.sin_intelisis, p.nota_medico, p.entrega_id, p.fecha, us.nombre_completo, uv.nombre_completo
                FROM quimio_preparacion p JOIN usuario us ON us.id = p.surtio_id JOIN usuario uv ON uv.id = p.verifico_id
                WHERE p.id = ?
                """, (rs, n) -> new QuimioResponse(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getBoolean(7), rs.getString(8), rs.getBoolean(9), rs.getString(10), Entrega.folio(rs.getLong(11)),
                rs.getTimestamp(12).toLocalDateTime(), rs.getString(13), rs.getString(14), renglones, partidas), id).get(0);
    }

    /** La segunda persona de Farmacia, con su propio usuario y contraseña (no puede ser quien surte). */
    private Usuario verificador(String nombreUsuario, String contrasena, Usuario surtio) {
        String usuario = ServicioInventarioFarmacia.texto(nombreUsuario);
        if (usuario == null || contrasena == null || contrasena.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La segunda persona debe escribir su usuario y contraseña");
        }
        Usuario verifico = usuarioRepository.findByNombreUsuario(usuario)
                .filter(Usuario::isActivo)
                .filter(u -> u.getBloqueadoHasta() == null || u.getBloqueadoHasta().isBefore(LocalDateTime.now()))
                .filter(u -> passwordEncoder.matches(contrasena, u.getContrasenaHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario o contraseña de quien verifica incorrectos"));
        if (verifico.getId().equals(surtio.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La verificación la hace otra persona, no quien surte");
        }
        if (verifico.getRol() == null || !ROL_FARMACIA.equalsIgnoreCase(verifico.getRol().getNombre())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quien verifica debe ser personal de Farmacia");
        }
        return verifico;
    }

    /** Coinciden si al menos dos palabras (nombre y apellido) están en los dos, sin acentos. */
    static boolean nombresCoinciden(String solicitud, String intelisis) {
        List<String> deSolicitud = palabras(solicitud);
        Set<String> deIntelisis = new HashSet<>(palabras(intelisis));
        long comunes = deSolicitud.stream().filter(deIntelisis::contains).count();
        return comunes >= Math.min(2, deSolicitud.size());
    }

    private static List<String> palabras(String nombre) {
        if (nombre == null) {
            return List.of();
        }
        String limpio = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ");
        return Arrays.stream(limpio.split("\\s+")).filter(p -> p.length() > 1).toList();
    }

    private static String obligatorio(String valor, String mensaje) {
        String limpio = ServicioInventarioFarmacia.texto(valor);
        if (limpio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        return limpio;
    }
}
