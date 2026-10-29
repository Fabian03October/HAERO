package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.farmacia.dto.CajaEntregaRequest;
import com.example.backend.farmacia.dto.CamaCarrito;
import com.example.backend.farmacia.dto.CandidatoCarrito;
import com.example.backend.farmacia.dto.CarritoResponse;
import com.example.backend.farmacia.dto.EntregaResponse;
import com.example.backend.farmacia.dto.EscanearCarritoRequest;
import com.example.backend.farmacia.dto.EscaneoCarritoResponse;
import com.example.backend.farmacia.dto.LineaCarrito;
import com.example.backend.farmacia.dto.PartidaDevueltaRequest;
import com.example.backend.farmacia.dto.QuitarCarritoRequest;
import com.example.backend.farmacia.dto.RegistrarDevolucionRequest;
import com.example.backend.farmacia.dto.RegistrarEntregaRequest;
import com.example.backend.farmacia.dto.SurtidoCarritoResponse;
import com.example.backend.farmacia.dto.SurtirCarritoRequest;
import com.example.backend.farmacia.entity.ClaveReceta;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.EntregaRepository;
import com.example.backend.farmacia.repository.RecetaRepository;
import com.example.backend.hospitalizacion.ServicioHospitalizacion;
import com.example.backend.hospitalizacion.ServicioSincronizacionHospitalizacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carrito del día de Hospitalización (reglas acordadas con Farmacia):
 *  - Se surte solo lo NUEVO: lo indicado desde las 12 de la noche de hoy; lo que el paciente ya
 *    venía tomando no vuelve a salir.
 *  - Más lo pendiente de ayer: lo indicado ayer que no se surtió (por ejemplo, lo de después de la
 *    1:00 p.m.).
 *  - Lo indicado hoy después de la hora de corte (1:00 p.m.) sale aparte: se surte si urge; si no,
 *    mañana sale como pendiente de ayer.
 *  - La cantidad la captura el despachador (como en ventanilla); la dosis es solo referencia.
 *  - Lo que se canceló después de meterlo al carrito se avisa y se puede sacar (regresa al inventario).
 *
 * Cada surtido es una entrega normal de Farmacia (receta "HOSP-AAAAMMDD-ingreso"): descuenta el
 * inventario por lote (GS1 o FEFO), entra al resumen del día, a la trazabilidad y a la hoja de baja.
 */
@Service
public class ServicioCarrito {

    static final String PREFIJO_FOLIO = "HOSP-";
    private static final DateTimeFormatter DIA = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate haero;
    private final RecetaRepository recetaRepository;
    private final EntregaRepository entregaRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final ServicioEntregas servicioEntregas;
    private final ServicioDevoluciones servicioDevoluciones;
    private final ServicioInventarioFarmacia inventario;
    // Hasta esta hora Farmacia surte el carrito; lo indicado después sale aparte.
    private final LocalTime horaCorte;

    public ServicioCarrito(JdbcTemplate haero,
                           RecetaRepository recetaRepository,
                           EntregaRepository entregaRepository,
                           MedicamentoRepository medicamentoRepository,
                           ServicioEntregas servicioEntregas,
                           ServicioDevoluciones servicioDevoluciones,
                           ServicioInventarioFarmacia inventario,
                           @Value("${hospitalizacion.carrito.hora-corte:13:00}") String horaCorte) {
        this.haero = haero;
        this.recetaRepository = recetaRepository;
        this.entregaRepository = entregaRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.servicioEntregas = servicioEntregas;
        this.servicioDevoluciones = servicioDevoluciones;
        this.inventario = inventario;
        this.horaCorte = LocalTime.parse(horaCorte);
    }

    // ---------- Lista del carrito ----------

    public CarritoResponse carrito(String central) {
        LocalDate hoy = LocalDate.now();
        Cierre cierreHoy = cierre(central, hoy);
        List<CamaCarrito> camas = new ArrayList<>();
        for (Internado internado : internados(central)) {
            List<LineaCarrito> lineas = lineas(internado.idIngreso, hoy);
            if (!lineas.isEmpty()) {
                camas.add(new CamaCarrito(internado.espacio, internado.idIngreso, internado.paciente, internado.expediente, lineas));
            }
        }
        int lineas = camas.stream().mapToInt(c -> (int) c.getLineas().stream().filter(l -> !"SUSPENDIDO".equals(l.getEstado())).count()).sum();
        int surtidas = camas.stream().mapToInt(c -> (int) c.getLineas().stream().filter(l -> "SURTIDO".equals(l.getEstado())).count()).sum();
        return new CarritoResponse(central, hoy, horaCorte.toString(), lineas, surtidas, cierreHoy == null ? null : cierreHoy.en,
                cierreHoy == null ? null : cierreHoy.por, camas);
    }

    private record Indicado(String clave, String nombre, String posologia, Integer dosis24h, LocalDateTime indicado) {
    }

    /** Lo que se surte hoy al paciente, agrupado por clave (ver reglas arriba). */
    List<LineaCarrito> lineas(int idIngreso, LocalDate dia) {
        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime corte = dia.atTime(horaCorte);
        LocalDateTime inicioAyer = inicioDia.minusDays(1);

        // Indicaciones activas; por clave, la más reciente (la que manda).
        Map<String, Indicado> activos = new LinkedHashMap<>();
        haero.query("""
                SELECT clave, medicamento, dosis, unidad, via, cada, cada_unidad, dosis_24h, indicado_en FROM hosp_medicamento
                WHERE id_ingreso = ? AND activo = 1 ORDER BY indicado_en
                """, rs -> {
            String clave = rs.getString(1);
            String llave = clave != null ? clave : "SIN-CLAVE:" + rs.getString(2);
            float dosis = rs.getFloat(3);
            Float d = rs.wasNull() ? null : dosis;
            int cada = rs.getInt(6);
            Integer c = rs.wasNull() ? null : cada;
            int dosis24 = rs.getInt(8);
            Integer d24 = rs.wasNull() ? null : dosis24;
            Timestamp indicado = rs.getTimestamp(9);
            activos.put(llave, new Indicado(clave, rs.getString(2),
                    ServicioSincronizacionHospitalizacion.posologia(d, rs.getString(4), rs.getString(5), c, rs.getString(7)),
                    d24, indicado == null ? null : indicado.toLocalDateTime()));
        }, idIngreso);

        Map<String, Integer> entregadas = entregadas(folio(dia, idIngreso));
        Map<String, Integer> entregadasAyer = entregadas(folio(dia.minusDays(1), idIngreso));
        Map<String, String> lotes = lotesEntregados(folio(dia, idIngreso));
        Set<String> modificados = new java.util.HashSet<>(haero.queryForList(
                "SELECT DISTINCT clave FROM hosp_evento WHERE id_ingreso = ? AND tipo = 'MEDICAMENTO_MODIFICADO' AND fecha >= ? AND clave IS NOT NULL",
                String.class, idIngreso, Timestamp.valueOf(inicioDia)));
        Set<String> enCatalogo = medicamentoRepository.findAllById(activos.keySet()).stream()
                .map(Medicamento::getClave).collect(Collectors.toSet());

        List<LineaCarrito> lineas = new ArrayList<>();
        activos.forEach((llave, m) -> {
            int entregado = entregadas.getOrDefault(llave, 0);
            LocalDateTime indicado = m.indicado();
            boolean deHoy = indicado != null && !indicado.isBefore(inicioDia);
            boolean despues = deHoy && !indicado.isBefore(corte);
            boolean deAyer = indicado != null && !indicado.isBefore(inicioAyer) && indicado.isBefore(inicioDia)
                    && entregadasAyer.getOrDefault(llave, 0) == 0;
            // Solo lo nuevo: lo de días anteriores ya se surtió (salvo que hoy se haya surtido algo).
            if (!deHoy && !deAyer && entregado == 0) {
                return;
            }
            boolean conClave = m.clave() != null;
            lineas.add(new LineaCarrito(m.clave(), m.nombre(), conClave && enCatalogo.contains(m.clave()), m.posologia(), m.dosis24h(),
                    entregado, entregado > 0 ? "SURTIDO" : "PENDIENTE", indicado, lotes.get(llave), despues && entregado == 0,
                    deAyer, conClave && modificados.contains(m.clave()), null, null));
        });

        // Lo que se metió hoy al carrito y después se canceló: avisar para sacarlo.
        entregadas.forEach((clave, piezas) -> {
            if (!activos.containsKey(clave) && piezas > 0) {
                Map<String, Object> cancelacion = cancelacion(idIngreso, clave);
                String nombre = medicamentoRepository.findById(clave).map(Medicamento::getNombreGenerico).orElse(clave);
                lineas.add(new LineaCarrito(clave, nombre, true, null, null, piezas, "SUSPENDIDO", null, lotes.get(clave), false, false, false,
                        cancelacion.get("en") instanceof Timestamp t ? t.toLocalDateTime() : null, (String) cancelacion.get("por")));
            }
        });
        // Primero lo pendiente de ayer, luego lo de hoy y al final lo de después de la 1:00 p.m.
        lineas.sort(Comparator.comparingInt((LineaCarrito l) -> l.isPendienteDeAyer() ? 0 : l.isDespuesDelCierre() ? 2 : 1));
        return lineas;
    }

    /** Cuándo y qué médico canceló el medicamento (el más reciente de esa clave). */
    private Map<String, Object> cancelacion(int idIngreso, String clave) {
        List<Map<String, Object>> filas = haero.queryForList("""
                SELECT TOP 1 m.suspendido_en AS en, COALESCE(md.nombre, m.suspendio) AS por
                FROM hosp_medicamento m LEFT JOIN hosp_medico md ON md.usuario = m.suspendio
                WHERE m.id_ingreso = ? AND m.clave = ? AND m.activo = 0
                ORDER BY m.suspendido_en DESC
                """, idIngreso, clave);
        return filas.isEmpty() ? Map.of() : filas.get(0);
    }

    private Map<String, Integer> entregadas(String folio) {
        Map<String, Integer> entregadas = new LinkedHashMap<>();
        haero.query("""
                SELECT rc.medicamento_clave, SUM(p.piezas - p.piezas_devueltas)
                FROM farmacia_entrega_partida p
                JOIN farmacia_entrega e ON e.id = p.entrega_id
                JOIN farmacia_receta r ON r.id = e.receta_id
                JOIN farmacia_receta_clave rc ON rc.id = p.receta_clave_id
                WHERE r.folio = ?
                GROUP BY rc.medicamento_clave
                """, rs -> {
            entregadas.put(rs.getString(1), rs.getInt(2));
        }, folio);
        return entregadas;
    }

    /** "A123 (3), B456 (1)" por clave, ya restadas las devoluciones. */
    private Map<String, String> lotesEntregados(String folio) {
        Map<String, List<String>> porClave = new LinkedHashMap<>();
        haero.query("""
                SELECT rc.medicamento_clave, fl.numero_lote, SUM(p.piezas - p.piezas_devueltas)
                FROM farmacia_entrega_partida p
                JOIN farmacia_entrega e ON e.id = p.entrega_id
                JOIN farmacia_receta r ON r.id = e.receta_id
                JOIN farmacia_receta_clave rc ON rc.id = p.receta_clave_id
                JOIN farmacia_lote fl ON fl.id = p.farmacia_lote_id
                WHERE r.folio = ?
                GROUP BY rc.medicamento_clave, fl.numero_lote
                HAVING SUM(p.piezas - p.piezas_devueltas) > 0
                """, rs -> {
            porClave.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2) + " (" + rs.getInt(3) + ")");
        }, folio);
        Map<String, String> lotes = new HashMap<>();
        porClave.forEach((clave, lista) -> lotes.put(clave, String.join(", ", lista)));
        return lotes;
    }

    /** Dosis en 24 horas según la frecuencia: c/8 h = 3. Sin frecuencia, una. Solo referencia. */
    static int dosisDelDia(Integer cada, String cadaUnidad) {
        if (cada == null || cada <= 0) {
            return 1;
        }
        String unidad = cadaUnidad == null ? "" : cadaUnidad.toLowerCase(Locale.ROOT);
        if (unidad.startsWith("min")) {
            return Math.max(1, (int) Math.ceil(24 * 60.0 / cada));
        }
        if (unidad.startsWith("d") || unidad.startsWith("sem") || unidad.startsWith("mes")) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(24.0 / cada));
    }

    // ---------- Escaneo ----------

    /** Reconoce la caja por su código de barras y dice a qué pacientes del área les toca. */
    public EscaneoCarritoResponse escanear(EscanearCarritoRequest request) {
        String codigo = ServicioInventarioFarmacia.texto(request.getCodigo());
        if (codigo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escanea el código de la caja");
        }
        Medicamento medicamento = inventario.buscarCodigo(codigo).map(CodigoBarras::getMedicamento).orElse(null);
        String claveElegida = ServicioInventarioFarmacia.texto(request.getClave());
        if (medicamento == null && claveElegida == null) {
            // 404 = código desconocido: la pantalla pide elegir de qué medicamento es (HU42).
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El código " + codigo + " todavía no está registrado. Elige qué medicamento es y queda guardado.");
        }
        boolean codigoNuevo = medicamento == null;
        if (codigoNuevo) {
            medicamento = medicamentoRepository.findById(claveElegida)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave " + claveElegida + " no está en el catálogo"));
        }
        final Medicamento elegido = medicamento;
        LocalDate hoy = LocalDate.now();
        List<CandidatoCarrito> candidatos = new ArrayList<>();
        List<String> cancelados = new ArrayList<>();
        for (Internado internado : internados(request.getCentral())) {
            List<LineaCarrito> lineas = lineas(internado.idIngreso, hoy);
            lineas.stream()
                    .filter(l -> elegido.getClave().equals(l.getClave()) && "PENDIENTE".equals(l.getEstado()))
                    .findFirst()
                    .ifPresent(l -> candidatos.add(new CandidatoCarrito(internado.idIngreso, internado.espacio, internado.paciente,
                            l.getDosis24h() == null ? 0 : l.getDosis24h(), l.getEntregadas())));
            if (candidatos.stream().noneMatch(c -> c.getIdIngreso() == internado.idIngreso)) {
                Map<String, Object> cancelacion = cancelacion(internado.idIngreso, elegido.getClave());
                if (cancelacion.get("en") instanceof Timestamp t && !t.toLocalDateTime().isBefore(hoy.atStartOfDay())) {
                    cancelados.add("cama " + internado.espacio + " (cancelado " + t.toLocalDateTime().toLocalTime().withNano(0)
                            + (cancelacion.get("por") != null ? " por " + cancelacion.get("por") : "") + ")");
                }
            }
        }
        if (candidatos.isEmpty()) {
            if (!cancelados.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, elegido.getClave() + " " + elegido.getNombreGenerico()
                        + " se canceló: " + String.join("; ", cancelados) + ". No lo pongas en el carrito.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja es de " + elegido.getClave() + " "
                    + elegido.getNombreGenerico() + " y ningún paciente del área la tiene pendiente");
        }
        // Se aprende solo si la clave elegida de verdad está pendiente en el área (HU42).
        if (codigoNuevo) {
            inventario.aprenderCodigo(codigo, elegido);
        }
        return new EscaneoCarritoResponse(elegido.getClave(), elegido.getNombreGenerico(),
                ServicioInventarioFarmacia.texto(request.getLote()), candidatos);
    }

    // ---------- Surtir y sacar del carrito ----------

    /** Mete al carrito lo que el despachador capturó: se descuenta del inventario de Farmacia. */
    @Transactional
    public SurtidoCarritoResponse surtir(SurtirCarritoRequest request, String nombreUsuario) {
        String clave = ServicioInventarioFarmacia.texto(request.getClave());
        if (clave == null || request.getPiezas() == null || request.getPiezas() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la clave y las piezas que se surten");
        }
        Internado internado = internado(request.getIdIngreso());
        LocalDate hoy = LocalDate.now();
        LineaCarrito linea = lineas(internado.idIngreso, hoy).stream()
                .filter(l -> clave.equals(l.getClave()) && !"SUSPENDIDO".equals(l.getEstado())).findFirst()
                .orElseThrow(() -> {
                    Map<String, Object> cancelacion = cancelacion(internado.idIngreso, clave);
                    if (cancelacion.get("en") != null) {
                        return new ResponseStatusException(HttpStatus.CONFLICT, clave + " se canceló para la cama " + internado.espacio
                                + (cancelacion.get("por") != null ? " (" + cancelacion.get("por") + ")" : "") + ". No lo pongas en el carrito.");
                    }
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El paciente de la cama " + internado.espacio + " no tiene " + clave + " por surtir hoy");
                });

        // La receta del día del paciente; lo "indicado" en HAERO es lo que el despachador decide surtir.
        String folio = folio(hoy, internado.idIngreso);
        Receta receta = recetaRepository.findByFolioIgnoreCase(folio).orElseGet(() -> {
            Receta nueva = new Receta();
            nueva.setFolio(folio);
            nueva.setPacienteNombre(internado.paciente != null ? internado.paciente : "Paciente cama " + internado.espacio);
            nueva.setPacienteExpediente(internado.expediente);
            nueva.setUsuario(inventario.usuario(nombreUsuario));
            return nueva;
        });
        ClaveReceta claveReceta = receta.getClaves().stream().filter(c -> c.getMedicamento().getClave().equals(clave)).findFirst()
                .orElseGet(() -> {
                    ClaveReceta nueva = new ClaveReceta();
                    nueva.setReceta(receta);
                    nueva.setMedicamento(inventario.medicamento(clave));
                    nueva.setPiezasIndicadas(request.getPiezas());
                    receta.getClaves().add(nueva);
                    return nueva;
                });
        claveReceta.setPiezasIndicadas(Math.max(claveReceta.getPiezasIndicadas(), claveReceta.getPiezasEntregadas() + request.getPiezas()));
        receta.setEstado(Receta.PARCIAL);
        recetaRepository.save(receta);

        RegistrarEntregaRequest entrega = new RegistrarEntregaRequest();
        entrega.setFolioReceta(folio);
        CajaEntregaRequest caja = new CajaEntregaRequest();
        caja.setClave(clave);
        caja.setLote(request.getLote());
        caja.setPiezas(request.getPiezas());
        entrega.setCajas(List.of(caja));
        EntregaResponse registrada = servicioEntregas.registrar(entrega, nombreUsuario);

        LineaCarrito actualizada = lineas(internado.idIngreso, hoy).stream()
                .filter(l -> clave.equals(l.getClave())).findFirst().orElse(linea);
        return new SurtidoCarritoResponse(registrada.getFolio(), registrada.getPartidas(), actualizada);
    }

    /**
     * Saca del carrito de hoy lo que se metió de esa clave (por ejemplo, porque el médico lo
     * canceló): se registra como devolución y regresa al mismo lote del inventario de Farmacia.
     */
    @Transactional
    public CarritoResponse quitar(QuitarCarritoRequest request, String nombreUsuario) {
        String clave = ServicioInventarioFarmacia.texto(request.getClave());
        if (request.getIdIngreso() == null || clave == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el paciente y la clave que se saca del carrito");
        }
        Receta receta = recetaRepository.findByFolioIgnoreCase(folio(LocalDate.now(), request.getIdIngreso()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese paciente no tiene nada en el carrito de hoy"));
        String motivo = ServicioInventarioFarmacia.texto(request.getMotivo());
        int sacadas = 0;
        for (Entrega entrega : entregaRepository.findByRecetaIdOrderByFechaAsc(receta.getId())) {
            List<PartidaDevueltaRequest> partidas = new ArrayList<>();
            for (PartidaEntrega partida : entrega.getPartidas()) {
                int pendiente = partida.getPiezas() - partida.getPiezasDevueltas();
                if (partida.getClaveReceta().getMedicamento().getClave().equals(clave) && pendiente > 0) {
                    PartidaDevueltaRequest devuelta = new PartidaDevueltaRequest();
                    devuelta.setPartidaId(partida.getId());
                    devuelta.setPiezas(pendiente);
                    partidas.add(devuelta);
                    sacadas += pendiente;
                }
            }
            if (!partidas.isEmpty()) {
                RegistrarDevolucionRequest devolucion = new RegistrarDevolucionRequest();
                devolucion.setEntregaId(entrega.getId());
                devolucion.setMotivo(motivo != null ? motivo : "Se sacó del carrito");
                devolucion.setPartidas(partidas);
                servicioDevoluciones.registrar(devolucion, nombreUsuario);
            }
        }
        if (sacadas == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay " + clave + " en el carrito de ese paciente");
        }
        // Lo entregado de la receta del día baja a lo que realmente va en el carrito.
        receta.getClaves().stream().filter(c -> c.getMedicamento().getClave().equals(clave)).findFirst().ifPresent(c -> {
            c.setPiezasEntregadas(0);
            c.setPiezasIndicadas(Math.max(1, c.getPiezasIndicadas()));
        });
        recetaRepository.save(receta);
        String central = haero.queryForObject("SELECT TOP 1 c.central FROM " + ServicioHospitalizacion.OCUPANTES
                + " o JOIN hosp_cama c ON c.espacio = o.espacio WHERE o.id_ingreso = ?", String.class, request.getIdIngreso());
        return carrito(central);
    }

    // ---------- Cierre ----------

    record Cierre(LocalDateTime en, String por) {
    }

    Cierre cierre(String central, LocalDate dia) {
        return haero.query("""
                SELECT c.cerrado_en, u.nombre_completo FROM farmacia_carrito_cierre c JOIN usuario u ON u.id = c.usuario_id
                WHERE c.central = ? AND c.dia = ?
                """, (rs, n) -> new Cierre(rs.getTimestamp(1).toLocalDateTime(), rs.getString(2)), central, java.sql.Date.valueOf(dia))
                .stream().findFirst().orElse(null);
    }

    void registrarCierre(String central, long usuarioId) {
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        java.sql.Date hoy = java.sql.Date.valueOf(LocalDate.now());
        if (haero.update("UPDATE farmacia_carrito_cierre SET cerrado_en = ?, usuario_id = ? WHERE central = ? AND dia = ?",
                ahora, usuarioId, central, hoy) == 0) {
            haero.update("INSERT INTO farmacia_carrito_cierre (central, dia, cerrado_en, usuario_id) VALUES (?, ?, ?, ?)",
                    central, hoy, ahora, usuarioId);
        }
    }

    // ---------- comunes ----------

    record Internado(int idIngreso, String espacio, String paciente, String expediente) {
    }

    /** Internados del área (una persona por cama: la de ingreso más reciente), en orden de cama. */
    List<Internado> internados(String central) {
        String area = ServicioInventarioFarmacia.texto(central);
        if (area == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el área del carrito");
        }
        List<Internado> lista = haero.query("SELECT e.id_ingreso, e.espacio, e.paciente, e.expediente FROM "
                + ServicioHospitalizacion.OCUPANTES + " e JOIN hosp_cama c ON c.espacio = e.espacio WHERE c.central = ?", (rs, n) -> new Internado(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), area);
        return lista.stream()
                .sorted((a, b) -> ServicioHospitalizacion.compararNatural(a.espacio, b.espacio))
                .toList();
    }

    Internado internado(int idIngreso) {
        return haero.query("SELECT TOP 1 e.id_ingreso, e.espacio, e.paciente, e.expediente FROM "
                + ServicioHospitalizacion.OCUPANTES + " e JOIN hosp_cama c ON c.espacio = e.espacio WHERE e.id_ingreso = ?", (rs, n) -> new Internado(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), idIngreso)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El paciente ya no está internado en una cama"));
    }

    static String folio(LocalDate dia, int idIngreso) {
        return PREFIJO_FOLIO + dia.format(DIA) + "-" + idIngreso;
    }
}
