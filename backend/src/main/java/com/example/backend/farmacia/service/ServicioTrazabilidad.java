package com.example.backend.farmacia.service;

import com.example.backend.intelisis.service.NombrePaciente;

import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.usuarios.entity.Usuario;
import com.example.backend.farmacia.dto.EntregaPacienteResponse;
import com.example.backend.farmacia.dto.LoteTrazableResponse;
import com.example.backend.farmacia.dto.RecetaIntelisisResponse;
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

import java.math.BigDecimal;
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
    private final ServicioBajasPacienteIntelisis bajasIntelisis;
    private final ServicioRecetasIntelisis recetasIntelisis;

    public ServicioTrazabilidad(ServicioBajasPacienteIntelisis bajasIntelisis,
                                ServicioRecetasIntelisis recetasIntelisis,
                                LoteFarmaciaRepository loteFarmaciaRepository,
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
        this.bajasIntelisis = bajasIntelisis;
        this.recetasIntelisis = recetasIntelisis;
    }

    @Transactional
    public TrazabilidadResponse porLote(String numeroLote, String clave, LocalDate desde, LocalDate hasta, String nombreUsuario) {
        Periodo periodo = Periodo.de(desde, hasta);
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
        // Lo que copió la sincronización de Intelisis: sirve para lotes anteriores a HAERO o que no pasaron por él.
        List<RecepcionIntelisis> recepciones = recepcionesIntelisis(lote, filtroClave);
        List<ExistenciaIntelisis> existencias = existenciasIntelisis(lote, filtroClave);
        List<MovimientoIntelisis> movimientos = movimientosFarmaciaIntelisis(lote, filtroClave);
        // Antes de que HAERO dispensara, el paciente de cada baja de Farmacia está en Intelisis.
        ServicioBajasPacienteIntelisis.Resultado anteriores = bajasIntelisis.delLote(lote, filtroClave, periodo.desde(), periodo.antesDe(inicioDispensacionHaero()));
        if (enFarmacia.isEmpty() && enAlmacen.isEmpty() && recepciones.isEmpty() && existencias.isEmpty() && movimientos.isEmpty()
                && anteriores.entregas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay registro del lote " + lote + " en HAERO ni en Intelisis");
        }

        List<Long> idsFarmacia = enFarmacia.stream().map(LoteFarmacia::getId).toList();
        List<PartidaEntrega> partidas = idsFarmacia.isEmpty() ? List.of() : partidaEntregaRepository.findByLoteIdIn(idsFarmacia);

        List<PasoRecorridoResponse> recorrido = new ArrayList<>();
        PasoRecorridoResponse pedido = pasoPedido(enAlmacen.values());
        recorrido.add(pedido.getFecha() == null && !recepciones.isEmpty() ? pasoPedidoIntelisis(recepciones) : pedido);
        recorrido.add(enAlmacen.isEmpty() && !recepciones.isEmpty() ? pasoEntradaIntelisis(recepciones) : pasoEntradaAlmacen(enAlmacen.values()));
        recorrido.add(pasoDespacho(enAlmacen.keySet()));
        recorrido.add(pasoRecepcionFarmacia(idsFarmacia));
        recorrido.add(pasoEntregas(partidas));
        if (!anteriores.entregas().isEmpty()) {
            recorrido.add(pasoEntregasIntelisis(anteriores.entregas()));
        }
        recorrido.add(movimientos.isEmpty() ? pasoBajaIntelisis(partidas) : pasoBajaDesdeIntelisis(movimientos));
        if (!existencias.isEmpty()) {
            recorrido.add(pasoExistenciaIntelisis(existencias));
        }

        List<TrazabilidadResponse.Compra> compras = recepciones.stream()
                .map(r -> new TrazabilidadResponse.Compra(r.fecha(), r.movId(),
                        r.origen() == null ? null : r.origen() + (r.origenId() != null ? " " + r.origenId() : ""),
                        r.contratoId(), r.proveedor(), r.clave(), r.cantidad(), r.caducidad(), r.almacen()))
                .toList();
        List<TrazabilidadResponse.Existencia> existenciasHoy = existencias.stream()
                .map(e -> new TrazabilidadResponse.Existencia(e.almacen(), e.articulo().endsWith(SUFIJO_FARMACIA) ? "Farmacia" : "Almacén",
                        e.clave(), e.existencia()))
                .toList();
        return new TrazabilidadResponse("LOTE", lote, recorrido, juntar(periodo.filtrar(partidas), anteriores), anteriores.aviso(), compras, existenciasHoy);
    }

    /** Periodo de las entregas a pacientes (días completos, ambos incluidos); sin fechas = todo. */
    record Periodo(LocalDateTime desde, LocalDateTime hasta) {
        static Periodo de(LocalDate desde, LocalDate hasta) {
            if (desde != null && hasta != null && hasta.isBefore(desde)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser antes de la inicial");
            }
            return new Periodo(desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay());
        }

        /** Límite superior para Intelisis: el fin del periodo o el inicio de HAERO, el que sea antes. */
        LocalDateTime antesDe(LocalDateTime inicioHaero) {
            return hasta == null ? inicioHaero : inicioHaero == null ? hasta : min(hasta, inicioHaero);
        }

        List<PartidaEntrega> filtrar(List<PartidaEntrega> partidas) {
            if (desde == null && hasta == null) {
                return partidas;
            }
            return partidas.stream().filter(p -> {
                LocalDateTime f = p.getEntrega().getFecha();
                return (desde == null || !f.isBefore(desde)) && (hasta == null || f.isBefore(hasta));
            }).toList();
        }
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /** Primera entrega registrada en HAERO: desde ahí manda HAERO; antes, las bajas de Intelisis. */
    private LocalDateTime inicioDispensacionHaero() {
        Timestamp primera = haero.queryForObject("SELECT MIN(fecha) FROM farmacia_entrega", Timestamp.class);
        return primera == null ? null : primera.toLocalDateTime();
    }

    /** Entregas de HAERO y de Intelisis en una sola lista, de la más reciente a la más antigua. */
    private static List<EntregaPacienteResponse> juntar(List<PartidaEntrega> partidas, ServicioBajasPacienteIntelisis.Resultado anteriores) {
        List<EntregaPacienteResponse> todas = new ArrayList<>(partidas.stream().map(ServicioTrazabilidad::aRespuesta).toList());
        todas.addAll(anteriores.entregas());
        todas.sort(Comparator.comparing(EntregaPacienteResponse::getFecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return todas;
    }

    private static PasoRecorridoResponse pasoEntregasIntelisis(List<EntregaPacienteResponse> entregas) {
        LocalDateTime primera = entregas.stream().map(EntregaPacienteResponse::getFecha).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        long pacientes = entregas.stream().map(e -> e.getExpediente() != null ? e.getExpediente() : e.getPaciente()).distinct().count();
        int piezas = entregas.stream().mapToInt(EntregaPacienteResponse::getPiezas).sum();
        return new PasoRecorridoResponse("Entregas a pacientes antes de HAERO", primera,
                entregas.size() + (entregas.size() == 1 ? " entrega" : " entregas") + " a " + pacientes
                        + (pacientes == 1 ? " paciente" : " pacientes") + ", " + piezas + " piezas (bajas de Farmacia en Intelisis)", "Intelisis");
    }

    @Transactional
    public TrazabilidadResponse porExpediente(String expediente, LocalDate desde, LocalDate hasta, String nombreUsuario) {
        Periodo periodo = Periodo.de(desde, hasta);
        String exp = ServicioInventarioFarmacia.texto(expediente);
        if (exp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el número de expediente");
        }
        registrarConsulta(nombreUsuario, "EXPEDIENTE", exp);
        List<PartidaEntrega> partidas = periodo.filtrar(partidaEntregaRepository.delPacientePorExpediente(exp));
        ServicioBajasPacienteIntelisis.Resultado anteriores = bajasIntelisis.delExpediente(exp, periodo.desde(), periodo.antesDe(inicioDispensacionHaero()));
        return new TrazabilidadResponse("EXPEDIENTE", exp, List.of(), juntar(partidas, anteriores), anteriores.aviso());
    }

    /**
     * Por nombre, en cualquier orden: "MARIA GARCIA" encuentra "GARCIA LOPEZ MARIA" y "María García López" (la misma persona
     * escrita de las dos formas, o guardada con menos palabras). Se busca por pares de palabras y se revisa con contiene().
     * shortcut: 200 partidas por par; si un par muy común llega a más, pasar el filtro completo a la consulta.
     */
    private List<PartidaEntrega> porPalabras(String texto) {
        Map<Long, PartidaEntrega> unicas = new java.util.LinkedHashMap<>();
        for (String[] par : NombrePaciente.pares(texto)) {
            partidaEntregaRepository.conPalabrasEnElNombre(par[0], par[1]).stream()
                    .filter(p -> NombrePaciente.contiene(p.getEntrega().getReceta().getPacienteNombre(), texto))
                    .forEach(p -> unicas.putIfAbsent(p.getId(), p));
        }
        return unicas.values().stream()
                .sorted(java.util.Comparator.comparing((PartidaEntrega p) -> p.getEntrega().getFecha()).reversed()).toList();
    }

    /** Por nombre del paciente (como se guardó al entregar) o por su CURP. */
    @Transactional
    public TrazabilidadResponse porPaciente(String paciente, LocalDate desde, LocalDate hasta, String nombreUsuario) {
        Periodo periodo = Periodo.de(desde, hasta);
        String texto = ServicioInventarioFarmacia.texto(paciente);
        if (texto == null || texto.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras del nombre o la CURP del paciente");
        }
        boolean esCurp = texto.length() == 18 && texto.matches("(?i)[A-Z]{4}\\d{6}[A-Z0-9]{8}");
        registrarConsulta(nombreUsuario, esCurp ? "CURP" : "NOMBRE", texto);
        // shortcut: se filtra el periodo sobre las 200 entregas más recientes; si un paciente llega a más, pasar el periodo a la consulta.
        List<PartidaEntrega> partidas = periodo.filtrar(esCurp
                ? partidaEntregaRepository.delPacientePorCurp(texto)
                : porPalabras(texto));
        LocalDateTime corte = periodo.antesDe(inicioDispensacionHaero());
        ServicioBajasPacienteIntelisis.Resultado anteriores = esCurp
                ? bajasIntelisis.delCurp(texto, periodo.desde(), corte) : bajasIntelisis.delPacientePorPalabras(texto, periodo.desde(), corte);
        return new TrazabilidadResponse(esCurp ? "CURP" : "NOMBRE", texto, List.of(), juntar(partidas, anteriores), anteriores.aviso());
    }

    /**
     * Por folio de receta de Intelisis: la receta (fecha, médico, medicamentos) y lo que Farmacia
     * le dio a ese paciente para surtirla. En HAERO, las entregas de esa receta; antes de HAERO,
     * las bajas de Farmacia a nombre del paciente desde el día de la receta hasta 7 días después,
     * de las claves recetadas (si ninguna coincide, todas las de esos días).
     */
    @Transactional
    public TrazabilidadResponse porReceta(String folio, String nombreUsuario) {
        String texto = ServicioInventarioFarmacia.texto(folio);
        if (texto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el folio de la receta");
        }
        // En Intelisis el folio tiene 6 dígitos con ceros a la izquierda ("074525").
        String buscado = texto.matches("\\d{1,5}") ? String.format("%06d", Integer.parseInt(texto)) : texto;
        registrarConsulta(nombreUsuario, "RECETA", buscado);
        RecetaIntelisisResponse receta = recetasIntelisis.porFolio(buscado);
        String folioReceta = receta.getFolio() != null ? receta.getFolio() : buscado;

        List<PartidaEntrega> partidas = partidaEntregaRepository.findByEntregaRecetaFolioIgnoreCaseOrderByEntregaFechaDesc(folioReceta);
        ServicioBajasPacienteIntelisis.Resultado anteriores = new ServicioBajasPacienteIntelisis.Resultado(List.of(), null);
        String nombrePaciente = receta.getPaciente() != null ? receta.getPaciente().getNombre() : null;
        if (nombrePaciente != null && receta.getFecha() != null) {
            LocalDateTime desde = receta.getFecha().toLocalDate().atStartOfDay();
            LocalDateTime hasta = desde.plusDays(DIAS_SURTIDO + 1);
            ServicioBajasPacienteIntelisis.Resultado delPaciente = bajasIntelisis.delPacientePorNombreExacto(nombrePaciente, desde, new Periodo(desde, hasta).antesDe(inicioDispensacionHaero()));
            List<EntregaPacienteResponse> enVentana = delPaciente.entregas().stream()
                    .filter(e -> e.getFecha() != null && !e.getFecha().isBefore(desde) && e.getFecha().isBefore(hasta)).toList();
            Set<String> claves = receta.getPartidas().stream().map(p -> p.getClave()).filter(Objects::nonNull).collect(Collectors.toSet());
            List<EntregaPacienteResponse> deLaReceta = enVentana.stream().filter(e -> claves.contains(e.getClave())).toList();
            anteriores = new ServicioBajasPacienteIntelisis.Resultado(deLaReceta.isEmpty() ? enVentana : deLaReceta, delPaciente.aviso());
        }

        String medicamentos = receta.getPartidas().stream()
                .map(p -> (p.getClave() != null ? p.getClave() + " " : "") + p.getMedicamento() + " ×" + p.getCantidad())
                .collect(Collectors.joining("; "));
        List<PasoRecorridoResponse> recorrido = new ArrayList<>();
        recorrido.add(new PasoRecorridoResponse("Receta " + folioReceta, receta.getFecha(),
                (receta.getOrigen() != null ? receta.getOrigen() + " · " : "") + (receta.getMedico() != null ? "Dr(a). " + receta.getMedico() + " · " : "")
                        + (medicamentos.isEmpty() ? "sin medicamentos" : medicamentos), "Intelisis"));
        List<EntregaPacienteResponse> entregas = juntar(partidas, anteriores);
        int piezas = entregas.stream().mapToInt(EntregaPacienteResponse::getPiezas).sum();
        recorrido.add(new PasoRecorridoResponse("Surtido en Farmacia",
                entregas.stream().map(EntregaPacienteResponse::getFecha).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),
                entregas.isEmpty() ? "Sin bajas de Farmacia a nombre del paciente en los " + DIAS_SURTIDO + " días siguientes a la receta"
                        : entregas.size() + (entregas.size() == 1 ? " renglón" : " renglones") + ", " + piezas + " piezas",
                entregas.isEmpty() ? null : (partidas.isEmpty() ? "Intelisis" : "HAERO")));
        String aviso = anteriores.aviso();
        if (nombrePaciente == null) {
            aviso = "La receta no tiene un paciente del padrón de Intelisis; no se pueden buscar sus bajas.";
        }
        return new TrazabilidadResponse("RECETA", folioReceta, recorrido, entregas, aviso);
    }

    // Días después de la receta en que se buscan las bajas de Farmacia del paciente.
    private static final int DIAS_SURTIDO = 7;

    /**
     * Lotes de un medicamento (por clave o nombre) o de un número de lote, juntando HAERO
     * (Almacén y Farmacia) con el espejo de Intelisis (existencias y recepciones). No toca
     * datos de pacientes, por eso no se registra como consulta.
     */
    @Transactional(readOnly = true)
    public List<LoteTrazableResponse> buscarLotes(String texto) {
        String q = ServicioInventarioFarmacia.texto(texto);
        if (q == null || q.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos 3 letras del medicamento, la clave o el lote");
        }
        // El lote se busca en cualquier parte del número (en Intelisis puede traer un prefijo: "2L23198 CCC").
        String inicio = q + "%";
        String contiene = "%" + q + "%";
        Map<String, LoteTrazable> lotes = new LinkedHashMap<>();

        haero.query("""
                SELECT m.clave, m.nombre_generico, fl.numero_lote, fl.caducidad, fl.piezas
                FROM farmacia_lote fl JOIN medicamento m ON m.clave = fl.medicamento_clave
                WHERE fl.numero_lote LIKE ? OR m.clave LIKE ? OR m.nombre_generico LIKE ?
                """, rs -> {
            LoteTrazable l = lote(lotes, rs.getString(1), rs.getString(2), rs.getString(3), fecha(rs.getDate(4)));
            l.enHaero = true;
            l.piezasFarmacia = (l.piezasFarmacia == null ? 0 : l.piezasFarmacia) + rs.getInt(5);
        }, contiene, inicio, contiene);

        haero.query("""
                SELECT m.clave, m.nombre_generico, l.numero_lote, l.caducidad
                FROM lote l JOIN medicamento m ON m.clave = l.medicamento_clave
                WHERE l.numero_lote LIKE ? OR m.clave LIKE ? OR m.nombre_generico LIKE ?
                """, rs -> {
            lote(lotes, rs.getString(1), rs.getString(2), rs.getString(3), fecha(rs.getDate(4))).enHaero = true;
        }, contiene, inicio, contiene);

        haero.query("""
                SELECT e.clave_base, COALESCE(im.nombre_generico, m.nombre_generico), e.serie_lote, MIN(e.fecha_caducidad),
                       SUM(CASE WHEN e.articulo = e.clave_base THEN e.existencia ELSE 0 END),
                       SUM(CASE WHEN e.articulo <> e.clave_base THEN e.existencia ELSE 0 END)
                FROM intelisis_existencia_lote e
                LEFT JOIN intelisis_medicamento im ON im.clave = e.clave_base
                LEFT JOIN medicamento m ON m.clave = e.clave_base
                WHERE e.serie_lote LIKE ? OR e.clave_base LIKE ? OR im.nombre_generico LIKE ? OR m.nombre_generico LIKE ?
                GROUP BY e.clave_base, COALESCE(im.nombre_generico, m.nombre_generico), e.serie_lote
                """, rs -> {
            LoteTrazable l = lote(lotes, rs.getString(1), rs.getString(2), rs.getString(3), fecha(rs.getDate(4)));
            l.enIntelisis = true;
            l.existenciaAlmacen = l.existenciaAlmacen.add(cero(rs.getBigDecimal(5)));
            l.existenciaFarmacia = l.existenciaFarmacia.add(cero(rs.getBigDecimal(6)));
        }, contiene, inicio, contiene, contiene);

        // Lotes que entraron por compra aunque ya no tengan existencia.
        haero.query("""
                SELECT r.articulo, COALESCE(im.nombre_generico, m.nombre_generico), r.serie_lote, MIN(r.fecha_caducidad)
                FROM intelisis_recepcion_lote r
                CROSS APPLY (SELECT CASE WHEN r.articulo LIKE '%-1' THEN LEFT(r.articulo, LEN(r.articulo) - 2) ELSE r.articulo END AS clave) b
                LEFT JOIN intelisis_medicamento im ON im.clave = b.clave
                LEFT JOIN medicamento m ON m.clave = b.clave
                WHERE r.serie_lote LIKE ? OR b.clave LIKE ? OR im.nombre_generico LIKE ? OR m.nombre_generico LIKE ?
                GROUP BY r.articulo, COALESCE(im.nombre_generico, m.nombre_generico), r.serie_lote
                """, rs -> {
            lote(lotes, claveBase(rs.getString(1)), rs.getString(2), rs.getString(3), fecha(rs.getDate(4))).enIntelisis = true;
        }, contiene, inicio, contiene, contiene);

        return lotes.values().stream()
                .sorted(Comparator.comparing((LoteTrazable l) -> l.clave)
                        .thenComparing(l -> l.caducidad, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(LIMITE_LOTES)
                .map(l -> new LoteTrazableResponse(l.clave, l.medicamento, l.lote,
                        l.sufijos.isEmpty() ? null : String.join(", ", l.sufijos), l.caducidad, l.piezasFarmacia,
                        l.enIntelisis ? l.existenciaAlmacen : null, l.enIntelisis ? l.existenciaFarmacia : null, l.enHaero, l.enIntelisis))
                .toList();
    }

    private static final int LIMITE_LOTES = 300;

    /** Lote acumulado de varias fuentes; la llave es clave + lote sin distinguir mayúsculas. */
    private static final class LoteTrazable {
        String clave;
        String medicamento;
        String lote;
        LocalDate caducidad;
        Integer piezasFarmacia;
        BigDecimal existenciaAlmacen = BigDecimal.ZERO;
        BigDecimal existenciaFarmacia = BigDecimal.ZERO;
        boolean enHaero;
        boolean enIntelisis;
        final Set<String> sufijos = new java.util.TreeSet<>();
    }

    private static LoteTrazable lote(Map<String, LoteTrazable> lotes, String clave, String medicamento, String numeroLote, LocalDate caducidad) {
        String c = clave == null ? "" : clave.trim();
        String n = loteBase(numeroLote);
        LoteTrazable l = lotes.computeIfAbsent(c + "|" + n.toUpperCase(), k -> {
            LoteTrazable nuevo = new LoteTrazable();
            nuevo.clave = c;
            nuevo.lote = n;
            return nuevo;
        });
        String sufijo = sufijoLote(numeroLote);
        if (sufijo != null) {
            l.sufijos.add(sufijo.toUpperCase());
        }
        if (l.medicamento == null && medicamento != null) {
            l.medicamento = medicamento.trim();
        }
        if (l.caducidad == null) {
            l.caducidad = caducidad;
        }
        return l;
    }

    // ---------- Espejo de Intelisis para el recorrido de un lote ----------

    private record RecepcionIntelisis(String clave, String movId, LocalDate fecha, String almacen, String proveedor,
                                      String origen, String origenId, Integer contratoId, BigDecimal cantidad, LocalDate caducidad) {
    }

    private record ExistenciaIntelisis(String clave, String almacen, String articulo, BigDecimal existencia) {
    }

    private record MovimientoIntelisis(String clave, String mov, LocalDate ultima, BigDecimal cantidad) {
    }

    private List<RecepcionIntelisis> recepcionesIntelisis(String lote, String clave) {
        return haero.query("""
                SELECT r.articulo, rc.mov_id, rc.fecha_emision, rc.almacen, COALESCE(p.nombre, rc.proveedor),
                       rc.origen, rc.origen_id, rc.contrato_id, r.cantidad, r.fecha_caducidad
                FROM intelisis_recepcion_lote r
                JOIN intelisis_recepcion rc ON rc.compra_id = r.compra_id
                LEFT JOIN intelisis_proveedor p ON p.proveedor = rc.proveedor
                WHERE (UPPER(r.serie_lote) = UPPER(?) OR UPPER(r.serie_lote) LIKE UPPER(?))
                ORDER BY rc.fecha_emision
                """, (rs, i) -> new RecepcionIntelisis(claveBase(rs.getString(1)), recortar(rs.getString(2)), fecha(rs.getDate(3)),
                recortar(rs.getString(4)), recortar(rs.getString(5)), recortar(rs.getString(6)), recortar(rs.getString(7)),
                (Integer) rs.getObject(8), cero(rs.getBigDecimal(9)), fecha(rs.getDate(10))), lote, conSufijo(lote))
                .stream().filter(r -> clave == null || clave.equals(r.clave())).toList();
    }

    private List<ExistenciaIntelisis> existenciasIntelisis(String lote, String clave) {
        return haero.query("""
                SELECT clave_base, almacen, articulo, existencia FROM intelisis_existencia_lote
                WHERE (UPPER(serie_lote) = UPPER(?) OR UPPER(serie_lote) LIKE UPPER(?)) AND existencia <> 0
                """, (rs, i) -> new ExistenciaIntelisis(recortar(rs.getString(1)), recortar(rs.getString(2)), recortar(rs.getString(3)),
                cero(rs.getBigDecimal(4))), lote, conSufijo(lote))
                .stream().filter(e -> clave == null || clave.equals(e.clave())).toList();
    }

    private List<MovimientoIntelisis> movimientosFarmaciaIntelisis(String lote, String clave) {
        return haero.query("""
                SELECT clave_base, mov, MAX(fecha_emision), SUM(ABS(cantidad)) FROM intelisis_mov_farmacia_lote
                WHERE (UPPER(serie_lote) = UPPER(?) OR UPPER(serie_lote) LIKE UPPER(?)) GROUP BY clave_base, mov
                """, (rs, i) -> new MovimientoIntelisis(recortar(rs.getString(1)), recortar(rs.getString(2)), fecha(rs.getDate(3)),
                cero(rs.getBigDecimal(4))), lote, conSufijo(lote))
                .stream().filter(m -> clave == null || clave.equals(m.clave())).toList();
    }

    private static PasoRecorridoResponse pasoPedidoIntelisis(List<RecepcionIntelisis> recepciones) {
        String detalle = recepciones.stream().map(r -> "Compra " + (r.movId() != null ? r.movId() : "s/n")
                        + (r.origen() != null ? " · " + r.origen() + (r.origenId() != null ? " " + r.origenId() : "") : "")
                        + (r.contratoId() != null ? " · contrato " + r.contratoId() : "")
                        + (r.proveedor() != null ? " · " + r.proveedor() : ""))
                .distinct().collect(Collectors.joining("; "));
        return new PasoRecorridoResponse("Pedido y contrato", inicioDelDia(recepciones.get(0).fecha()), detalle, "Intelisis");
    }

    private static PasoRecorridoResponse pasoEntradaIntelisis(List<RecepcionIntelisis> recepciones) {
        String detalle = recepciones.stream().map(r -> r.clave() + " · " + numero(r.cantidad()) + " recibidas"
                        + (r.caducidad() != null ? " · caduca " + r.caducidad() : "")
                        + (r.almacen() != null ? " · almacén " + r.almacen() : ""))
                .collect(Collectors.joining("; "));
        return new PasoRecorridoResponse("Entrada a Almacén", inicioDelDia(recepciones.get(0).fecha()), detalle, "Intelisis");
    }

    private static PasoRecorridoResponse pasoBajaDesdeIntelisis(List<MovimientoIntelisis> movimientos) {
        LocalDate ultima = movimientos.stream().map(MovimientoIntelisis::ultima).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String detalle = movimientos.stream().map(m -> m.mov() + ": " + numero(m.cantidad()) + " piezas")
                .collect(Collectors.joining("; ")) + " (movimientos de los últimos días que copia la sincronización)";
        return new PasoRecorridoResponse("Baja en Intelisis", inicioDelDia(ultima), detalle, "Intelisis");
    }

    private static PasoRecorridoResponse pasoExistenciaIntelisis(List<ExistenciaIntelisis> existencias) {
        String detalle = existencias.stream().map(e -> (e.articulo().endsWith(SUFIJO_FARMACIA) ? "Farmacia" : "Almacén") + " "
                        + e.almacen() + ": " + numero(e.existencia()))
                .collect(Collectors.joining("; "));
        return new PasoRecorridoResponse("Existencia hoy", null, detalle, "Intelisis");
    }

    /**
     * En Intelisis la mayoría de los lotes llevan el origen del recurso después de un espacio
     * ("6020174 SCC", "A123 UNOPS"); la caja escaneada trae solo "6020174". Devuelve el número
     * sin ese sufijo.
     */
    // shortcut: corta en la primera "/" o el primer espacio aunque no sea un sufijo; si aparecen lotes con espacios propios, limitar a la lista de sufijos.
    public static String loteBase(String lote) {
        String l = lote == null ? "" : lote.trim();
        int corte = corteSufijo(l);
        return corte > 0 ? l.substring(0, corte).trim() : l;
    }

    static String sufijoLote(String lote) {
        String l = lote == null ? "" : lote.trim();
        int corte = corteSufijo(l);
        return corte > 0 ? l.substring(corte + 1).trim() : null;
    }

    // El sufijo empieza en la primera "/" o el primer espacio: "F226C012/SADMI/SCC", "6020174 SCC", "1L26073 SCC SAEAC".
    private static int corteSufijo(String lote) {
        int diagonal = lote.indexOf('/');
        int espacio = lote.indexOf(' ');
        if (diagonal <= 0) return espacio;
        return espacio <= 0 ? diagonal : Math.min(diagonal, espacio);
    }

    /** Patrón LIKE para "el lote seguido de un sufijo", escapando los comodines de SQL Server. */
    private static String conSufijo(String lote) {
        return lote.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "[ /]%";
    }

    // En Intelisis la clave de Farmacia es la de Almacén con "-1" al final.
    private static final String SUFIJO_FARMACIA = "-1";

    static String claveBase(String articulo) {
        String a = recortar(articulo);
        return a != null && a.endsWith(SUFIJO_FARMACIA) ? a.substring(0, a.length() - SUFIJO_FARMACIA.length()) : a;
    }

    private static String recortar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static LocalDate fecha(java.sql.Date valor) {
        return valor == null ? null : valor.toLocalDate();
    }

    private static LocalDateTime inicioDelDia(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay();
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
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
                partida.getPiezas() - partida.getPiezasDevueltas(), entrega.getUsuario().getNombreCompleto(), "HAERO");
    }
}
