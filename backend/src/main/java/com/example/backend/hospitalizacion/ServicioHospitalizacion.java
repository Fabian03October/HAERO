package com.example.backend.hospitalizacion;

import com.example.backend.entity.Usuario;
import com.example.backend.hospitalizacion.dto.AreaHospitalizacion;
import com.example.backend.hospitalizacion.dto.CamaHospitalizacion;
import com.example.backend.hospitalizacion.dto.DetalleCama;
import com.example.backend.hospitalizacion.dto.DetallePaciente;
import com.example.backend.hospitalizacion.dto.EstadoHospitalizacion;
import com.example.backend.hospitalizacion.dto.EventoHospitalizacion;
import com.example.backend.hospitalizacion.dto.MedicamentoIndicado;
import com.example.backend.hospitalizacion.dto.PacienteInternado;
import com.example.backend.intelisis.ConexionIntelisis;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultas de Hospitalización para la pantalla de camas: áreas, camas de un área, detalle
 * de una cama (paciente, medicamentos y actualizaciones) y novedades sin ver.
 *
 * Todo sale de la foto que guarda ServicioSincronizacionHospitalizacion (nunca de Intelisis
 * en vivo). Son datos clínicos: solo Farmacia, con las políticas de uso
 * aceptadas, y cada vez que se abre el detalle de una cama queda en hosp_consulta.
 */
@Service
public class ServicioHospitalizacion {

    /**
     * Quién está hoy en cada cama: la estancia activa de ingreso más reciente. Intelisis deja
     * estancias sin cerrar (algunas de años atrás) en la misma cama; esas no se muestran.
     */
    public static final String OCUPANTES = "(SELECT * FROM (SELECT h.*, ROW_NUMBER() OVER (PARTITION BY h.espacio "
            + "ORDER BY h.fecha_ingreso DESC, h.id_ing_area DESC) AS orden FROM hosp_estancia h WHERE h.activa = 1) x WHERE x.orden = 1)";
    // Las notificaciones son de indicaciones médicas (medicamentos nuevos o suspendidos) desde las 12 de la noche.
    private static final String TIPOS_NOVEDAD = "('MEDICAMENTO_NUEVO', 'MEDICAMENTO_SUSPENDIDO', 'MEDICAMENTO_MODIFICADO')";
    private static final Duration ACTUALIZACIONES_EN_DETALLE = Duration.ofDays(7);
    private static final Duration SUSPENDIDOS_EN_DETALLE = Duration.ofHours(48);

    // Nombres de las centrales de enfermería que se conocen; las demás usan su servicio más común.
    private static final Map<String, String> NOMBRES = Map.ofEntries(
            Map.entry("CE-AMC", "Atención Médica Continua"),
            Map.entry("CE-HCLIN", "Hospitalización Clínicos"),
            Map.entry("CE-HQx", "Hospitalización Quirúrgicos"),
            Map.entry("CE-ONCO", "Oncología"),
            Map.entry("CE-UHD", "Unidad de Hospitalización de Día"),
            Map.entry("CE-TI", "Terapia Intensiva"),
            Map.entry("CE-TIPQx", "Terapia Intermedia"),
            Map.entry("CE-RECU", "Recuperación"),
            Map.entry("CE-Qx", "Quirófano"),
            Map.entry("CE-DIAL", "Diálisis"));

    private final JdbcTemplate haero;
    private final UsuarioRepository usuarioRepository;
    private final ConexionIntelisis conexion;
    private final ServicioSincronizacionHospitalizacion sincronizacion;
    private final String versionPoliticas;
    // Centrales de enfermería que Farmacia no atiende: no se muestran (se siguen sincronizando).
    private final List<String> areasOcultas;

    public ServicioHospitalizacion(JdbcTemplate haero,
                                   UsuarioRepository usuarioRepository,
                                   ConexionIntelisis conexion,
                                   ServicioSincronizacionHospitalizacion sincronizacion,
                                   @Value("${hospitalizacion.version-politicas:${politicas.version:1.0}}") String versionPoliticas,
                                   @Value("${hospitalizacion.areas-ocultas:CE-HEMO,CE-HEMOD,CE-DIAL,CE-ONCO,CE-Qx,CE-RECU}") List<String> areasOcultas) {
        this.haero = haero;
        this.usuarioRepository = usuarioRepository;
        this.conexion = conexion;
        this.sincronizacion = sincronizacion;
        this.versionPoliticas = versionPoliticas;
        this.areasOcultas = areasOcultas;
    }

    // ---------- Áreas ----------

    public List<AreaHospitalizacion> areas(String nombreUsuario) {
        Usuario usuario = autorizado(nombreUsuario);
        Foto foto = foto(usuario);
        Map<String, List<CamaHospitalizacion>> porArea = foto.camas().stream()
                .collect(Collectors.groupingBy(CamaHospitalizacion::getCentral, LinkedHashMap::new, Collectors.toList()));
        return porArea.entrySet().stream()
                .map(e -> {
                    List<CamaHospitalizacion> camas = e.getValue();
                    int ocupadas = (int) camas.stream().filter(c -> !c.getPacientes().isEmpty()).count();
                    int pacientes = camas.stream().mapToInt(c -> c.getPacientes().size()).sum();
                    int conNovedades = (int) camas.stream().filter(c -> c.getNovedades() > 0).count();
                    return new AreaHospitalizacion(e.getKey(), nombreDeArea(e.getKey(), camas), camas.size(), ocupadas,
                            camas.size() - ocupadas, pacientes, conNovedades);
                })
                .sorted(Comparator.comparing(AreaHospitalizacion::getNombre))
                .toList();
    }

    // ---------- Camas de un área ----------

    public List<CamaHospitalizacion> camas(String central, String nombreUsuario) {
        Usuario usuario = autorizado(nombreUsuario);
        return foto(usuario).camas().stream().filter(c -> c.getCentral().equals(central)).toList();
    }

    // ---------- Detalle de una cama ----------

    public DetalleCama detalle(String espacio, String nombreUsuario) {
        Usuario usuario = autorizado(nombreUsuario);
        CamaHospitalizacion cama = foto(usuario).camas().stream().filter(c -> c.getEspacio().equals(espacio)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la cama " + espacio));
        // Quién vio los datos de qué paciente (CA de privacidad).
        haero.update("INSERT INTO hosp_consulta (usuario_id, espacio, fecha) VALUES (?, ?, ?)",
                usuario.getId(), espacio, Timestamp.valueOf(LocalDateTime.now()));

        List<DetallePaciente> pacientes = new ArrayList<>();
        Timestamp suspendidosDesde = Timestamp.valueOf(LocalDateTime.now().minus(SUSPENDIDOS_EN_DETALLE));
        for (PacienteInternado paciente : cama.getPacientes()) {
            List<MedicamentoIndicado> medicamentos = haero.query("""
                    SELECT id, clave, medicamento, dosis, unidad, via, cada, cada_unidad, indicado_en, activo, suspendido_en
                    FROM hosp_medicamento
                    WHERE id_ingreso = ? AND (activo = 1 OR suspendido_en >= ?)
                    ORDER BY activo DESC, indicado_en DESC
                    """, (rs, n) -> medicamento(rs), paciente.getIdIngreso(), suspendidosDesde);
            pacientes.add(new DetallePaciente(paciente, medicamentos));
        }

        List<Integer> ingresos = cama.getPacientes().stream().map(PacienteInternado::getIdIngreso).toList();
        List<Object> parametros = new ArrayList<>(List.of(usuario.getId(), Timestamp.valueOf(LocalDateTime.now().minus(ACTUALIZACIONES_EN_DETALLE))));
        String where;
        if (ingresos.isEmpty()) {
            // Cama libre: sus movimientos recientes (quién salió y cuándo).
            where = "WHERE e.fecha >= ? AND e.espacio = ? AND e.tipo IN ('INGRESO', 'EGRESO', 'CAMBIO_CAMA')";
            parametros.add(espacio);
        } else {
            // Solo lo del paciente actual, más la última salida de otro paciente de esta cama,
            // para que no parezca que lo de un paciente anterior es del que está hoy.
            String marcas = ingresos.stream().map(i -> "?").collect(Collectors.joining(", "));
            where = "WHERE e.fecha >= ? AND (e.id_ingreso IN (" + marcas + ") OR e.id = (SELECT TOP 1 x.id FROM hosp_evento x "
                    + "WHERE x.espacio = ? AND x.tipo IN ('EGRESO', 'CAMBIO_CAMA') AND x.id_ingreso NOT IN (" + marcas + ") ORDER BY x.fecha DESC, x.id DESC))";
            parametros.addAll(ingresos);
            parametros.add(espacio);
            parametros.addAll(ingresos);
        }
        List<EventoHospitalizacion> actualizaciones = eventos(where, parametros, 100);
        return new DetalleCama(cama, pacientes, actualizaciones, ultimaLectura());
    }

    /** Apaga el aviso de la cama para este usuario. */
    public void marcarVisto(String espacio, String nombreUsuario) {
        Usuario usuario = autorizado(nombreUsuario);
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        int cambiados = haero.update("UPDATE hosp_visto SET visto_hasta = ? WHERE usuario_id = ? AND espacio = ?", ahora, usuario.getId(), espacio);
        if (cambiados == 0) {
            haero.update("INSERT INTO hosp_visto (usuario_id, espacio, visto_hasta) VALUES (?, ?, ?)", usuario.getId(), espacio, ahora);
        }
    }

    // ---------- Novedades ----------

    /**
     * Indicaciones médicas nuevas o suspendidas desde las 12 de la noche (de un área, si se
     * indica), solo de quien está hoy en cada cama; las más recientes primero.
     */
    public List<EventoHospitalizacion> novedades(String central, String nombreUsuario) {
        Usuario usuario = autorizado(nombreUsuario);
        List<Object> parametros = new ArrayList<>(List.of(usuario.getId(), Timestamp.valueOf(LocalDate.now().atStartOfDay())));
        String where = "WHERE e.fecha >= ? AND e.tipo IN " + TIPOS_NOVEDAD
                + " AND e.id_ingreso IN (SELECT o.id_ingreso FROM " + OCUPANTES + " o JOIN hosp_cama c ON c.espacio = o.espacio)";
        if (central != null && !central.isBlank()) {
            where += " AND e.central = ?";
            parametros.add(central);
        }
        return eventos(where, parametros, 200);
    }

    public EstadoHospitalizacion estado(String nombreUsuario) {
        autorizado(nombreUsuario);
        List<Map<String, Object>> ultima = haero.queryForList(
                "SELECT TOP 1 estatus, error FROM hosp_sincronizacion WHERE estatus <> 'EN_CURSO' ORDER BY id DESC");
        Integer camas = haero.queryForObject("SELECT COUNT(*) FROM hosp_cama", Integer.class);
        Integer internados = haero.queryForObject("SELECT COUNT(*) FROM " + OCUPANTES + " o JOIN hosp_cama c ON c.espacio = o.espacio", Integer.class);
        return new EstadoHospitalizacion(conexion.disponible(), sincronizacion.estaEnCurso(), ultimaLectura(),
                ultima.isEmpty() ? null : (String) ultima.get(0).get("estatus"),
                ultima.isEmpty() ? null : (String) ultima.get(0).get("error"),
                camas == null ? 0 : camas, internados == null ? 0 : internados);
    }

    // ---------- Foto actual ----------

    private record Foto(List<CamaHospitalizacion> camas) {
    }

    /** Camas con sus pacientes y novedades sin ver. Los internados sin cama del catálogo no se muestran. */
    private Foto foto(Usuario usuario) {
        LocalDateTime ahora = LocalDateTime.now();
        Map<Integer, Integer> medicamentosActivos = new HashMap<>();
        haero.query("SELECT id_ingreso, COUNT(*) FROM hosp_medicamento WHERE activo = 1 GROUP BY id_ingreso",
                rs -> {
                    medicamentosActivos.put(rs.getInt(1), rs.getInt(2));
                });
        Map<String, List<PacienteInternado>> pacientesPorCama = new HashMap<>();
        haero.query("SELECT id_ingreso, paciente, expediente, servicio, fecha_ingreso, espacio FROM " + OCUPANTES + " o", rs -> {
            Timestamp ingreso = rs.getTimestamp(5);
            LocalDateTime fecha = ingreso == null ? null : ingreso.toLocalDateTime();
            pacientesPorCama.computeIfAbsent(Objects.requireNonNullElse(rs.getString(6), ""), k -> new ArrayList<>())
                    .add(new PacienteInternado(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), fecha,
                            fecha == null ? null : (int) Duration.between(fecha, ahora).toDays(),
                            medicamentosActivos.getOrDefault(rs.getInt(1), 0)));
        });
        Map<String, Integer> novedades = new HashMap<>();
        Timestamp hoy = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        haero.query("SELECT o.espacio, COUNT(*) FROM hosp_evento e JOIN " + OCUPANTES + " o ON o.id_ingreso = e.id_ingreso "
                + "LEFT JOIN hosp_visto v ON v.espacio = o.espacio AND v.usuario_id = ? "
                + "WHERE e.tipo IN " + TIPOS_NOVEDAD + " AND e.fecha > CASE WHEN v.visto_hasta > ? THEN v.visto_hasta ELSE ? END "
                + "GROUP BY o.espacio", rs -> {
            novedades.put(rs.getString(1), rs.getInt(2));
        }, usuario.getId(), hoy, hoy);

        List<CamaHospitalizacion> camas = new ArrayList<>(haero.query(
                "SELECT espacio, nombre, tipo, central FROM hosp_cama", (rs, n) -> {
                    String espacio = rs.getString(1);
                    List<PacienteInternado> pacientes = pacientesPorCama.getOrDefault(espacio, List.of());
                    return new CamaHospitalizacion(espacio, rs.getString(2), rs.getString(3), rs.getString(4),
                            pacientes.isEmpty() ? "LIBRE" : "OCUPADA", pacientes, novedades.getOrDefault(espacio, 0));
                }));
        camas.removeIf(c -> areasOcultas.contains(c.getCentral()));
        camas.sort(Comparator.comparing(CamaHospitalizacion::getCentral).thenComparing(c -> c.getEspacio(), ServicioHospitalizacion::compararNatural));
        return new Foto(camas);
    }

    private List<EventoHospitalizacion> eventos(String where, List<Object> parametros, int maximo) {
        String sql = "SELECT TOP (" + maximo + ") e.id, e.fecha, e.tipo, e.espacio, e.central, e.detalle, "
                + "(SELECT TOP 1 h.paciente FROM hosp_estancia h WHERE h.id_ingreso = e.id_ingreso ORDER BY h.activa DESC) AS paciente, "
                + "CASE WHEN v.visto_hasta IS NOT NULL AND e.fecha <= v.visto_hasta THEN 1 ELSE 0 END AS visto, "
                + "COALESCE(md.nombre, e.medico) AS medico "
                + "FROM hosp_evento e LEFT JOIN hosp_visto v ON v.espacio = e.espacio AND v.usuario_id = ? "
                + "LEFT JOIN hosp_medico md ON md.usuario = e.medico "
                + where + " ORDER BY e.fecha DESC, e.id DESC";
        return haero.query(sql, (rs, n) -> new EventoHospitalizacion(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(7), rs.getString(6), rs.getInt(8) == 1, rs.getString(9)), parametros.toArray());
    }

    private MedicamentoIndicado medicamento(ResultSet rs) throws SQLException {
        float dosis = rs.getFloat(4);
        Float dosisONull = rs.wasNull() ? null : dosis;
        int cada = rs.getInt(7);
        Integer cadaONull = rs.wasNull() ? null : cada;
        Timestamp indicado = rs.getTimestamp(9);
        Timestamp suspendido = rs.getTimestamp(11);
        var m = new ServicioSincronizacionHospitalizacion.Medicamento(rs.getInt(1), 0, 0, rs.getString(2), rs.getString(3), rs.getString(6),
                dosisONull, rs.getString(5), cadaONull, rs.getString(8), null, rs.getBoolean(10), null);
        return new MedicamentoIndicado(rs.getInt(1), rs.getString(2), rs.getString(3), m.descripcion(), dosisONull, rs.getString(5),
                rs.getString(6), cadaONull, rs.getString(8), indicado == null ? null : indicado.toLocalDateTime(), rs.getBoolean(10),
                suspendido == null ? null : suspendido.toLocalDateTime());
    }

    private LocalDateTime ultimaLectura() {
        List<Timestamp> fin = haero.queryForList("SELECT TOP 1 fin FROM hosp_sincronizacion WHERE estatus = 'OK' ORDER BY id DESC", Timestamp.class);
        return fin.isEmpty() || fin.get(0) == null ? null : fin.get(0).toLocalDateTime();
    }

    private String nombreDeArea(String central, List<CamaHospitalizacion> camas) {
        String conocido = NOMBRES.get(central);
        if (conocido != null) {
            return conocido;
        }
        // El servicio más común de sus pacientes; si está vacía, el código de la central.
        return camas.stream().flatMap(c -> c.getPacientes().stream()).map(PacienteInternado::getServicio).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(central);
    }

    /** Usuario con las políticas de uso y privacidad vigentes aceptadas (son datos clínicos). */
    private Usuario autorizado(String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (!versionPoliticas.equals(usuario.getVersionPoliticasAceptada())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Para ver Hospitalización acepta las políticas de uso y privacidad vigentes");
        }
        return usuario;
    }

    /** "A-2" antes que "A-10". */
    public static int compararNatural(String a, String b) {
        String[] pa = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] pb = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int c = pa[i].matches("\\d+") && pb[i].matches("\\d+")
                    ? Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i])) : pa[i].compareToIgnoreCase(pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }
}
