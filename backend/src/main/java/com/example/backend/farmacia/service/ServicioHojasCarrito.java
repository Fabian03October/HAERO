package com.example.backend.farmacia.service;

import com.example.backend.farmacia.dto.CarritoResponse;
import com.example.backend.farmacia.dto.HojaBajaResponse;
import com.example.backend.farmacia.dto.HojaIndicacionesResponse;
import com.example.backend.farmacia.dto.LineaCarrito;
import com.example.backend.farmacia.dto.MedicamentoHojaResponse;
import com.example.backend.farmacia.dto.MedicoHojaResponse;
import com.example.backend.farmacia.dto.PartidaBajaResponse;
import com.example.backend.hospitalizacion.ServicioSincronizacionHospitalizacion;
import com.example.backend.intelisis.ConexionIntelisis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que se imprime del carrito de Hospitalización:
 *  - Antes de armarlo: "Indicaciones médicas activas", igual a la de Intelisis (una por paciente).
 *    Indicaciones generales, dieta, médicos, sexo y fecha de nacimiento se LEEN de Intelisis en
 *    ese momento y no se guardan; los medicamentos salen de lo que HAERO ya guarda.
 *  - Al terminarlo: la hoja de baja propia de HAERO (mismo acomodo que "Consumo Farmacia"), una
 *    por paciente y día, con folio consecutivo de HAERO. Precio y unidad se leen de Intelisis.
 *
 * Validado (2026-10-28): el precio es ArtCosto.CostoPromedio de Farmacia (empresa NBR, sucursal 0),
 * el mismo con que se captura el "Consumo Farmacia"; los médicos salen de Usuario + Personal.
 */
@Service
public class ServicioHojasCarrito {

    private static final Logger log = LoggerFactory.getLogger(ServicioHojasCarrito.class);
    private static final DateTimeFormatter INICIA = DateTimeFormatter.ofPattern("dd/MMM/yy HH:mm", Locale.forLanguageTag("es-MX"));
    private static final DateTimeFormatter OBSERVACIONES = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate haero;
    private final ConexionIntelisis conexion;
    private final ServicioCarrito carrito;
    private final ServicioInventarioFarmacia inventario;
    // Empresa y sucursal de Farmacia en Intelisis: su ArtCosto es el que usa el "Consumo Farmacia".
    private final String empresa;
    private final int sucursal;

    public ServicioHojasCarrito(JdbcTemplate haero, ConexionIntelisis conexion, ServicioCarrito carrito, ServicioInventarioFarmacia inventario,
                                @org.springframework.beans.factory.annotation.Value("${intelisis.empresa:NBR}") String empresa,
                                @org.springframework.beans.factory.annotation.Value("${intelisis.sucursal:0}") int sucursal) {
        this.haero = haero;
        this.conexion = conexion;
        this.carrito = carrito;
        this.inventario = inventario;
        this.empresa = empresa;
        this.sucursal = sucursal;
    }

    // ---------- Indicaciones médicas activas ----------

    /** Una hoja por paciente del área; con "ingresos" solo los elegidos. */
    public List<HojaIndicacionesResponse> indicaciones(String central, Collection<Integer> ingresos) {
        List<ServicioCarrito.Internado> internados = carrito.internados(central).stream()
                .filter(i -> ingresos == null || ingresos.isEmpty() || ingresos.contains(i.idIngreso()))
                .toList();
        if (internados.isEmpty()) {
            return List.of();
        }
        DeIntelisis intelisis = leerDeIntelisis(internados);
        LocalDateTime ahora = LocalDateTime.now();
        List<HojaIndicacionesResponse> hojas = new ArrayList<>();
        for (ServicioCarrito.Internado internado : internados) {
            List<MedicamentoHojaResponse> porHorario = new ArrayList<>();
            List<MedicamentoHojaResponse> porInfusion = new ArrayList<>();
            haero.query("""
                    SELECT clave, medicamento, dosis, unidad, via, cada, cada_unidad, dosis_24h, unidosis, inicia, indicado_en, nota,
                           externo, folio, infusion, numero_horas
                    FROM hosp_medicamento WHERE id_ingreso = ? AND activo = 1 ORDER BY COALESCE(inicia, indicado_en), id
                    """, rs -> {
                boolean infusion = rs.getBoolean(15);
                Timestamp inicia = rs.getTimestamp(10) != null ? rs.getTimestamp(10) : rs.getTimestamp(11);
                StringBuilder texto = new StringBuilder();
                float dosis = rs.getFloat(3);
                if (!rs.wasNull()) {
                    texto.append(dosis % 1 == 0 ? String.valueOf((int) dosis) : String.valueOf(dosis));
                    if (rs.getString(4) != null) texto.append(' ').append(rs.getString(4));
                }
                if (rs.getString(5) != null) texto.append(" Vía ").append(rs.getString(5));
                if (infusion) {
                    int horas = rs.getInt(16);
                    if (!rs.wasNull()) texto.append(" para ").append(horas).append(" horas");
                } else if (rs.getBoolean(9)) {
                    texto.append(" Unidosis");
                } else {
                    int cada = rs.getInt(6);
                    if (!rs.wasNull() && cada > 0) {
                        texto.append(" Cada ").append(cada).append(' ').append(rs.getString(7) != null ? rs.getString(7) : "horas").append(';');
                    }
                    int dosis24 = rs.getInt(8);
                    if (!rs.wasNull()) texto.append(' ').append(dosis24).append(" Dosis para 24 horas;");
                }
                if (inicia != null) texto.append("  Inicia: ").append(inicia.toLocalDateTime().format(INICIA));
                int folio = rs.getInt(14);
                Integer folioONull = rs.wasNull() ? null : folio;
                MedicamentoHojaResponse m = new MedicamentoHojaResponse(rs.getString(1), rs.getString(2), texto.toString().strip(),
                        rs.getString(12), rs.getBoolean(13), folioONull);
                (infusion ? porInfusion : porHorario).add(m);
            }, internado.idIngreso());

            Map<String, Object> paciente = intelisis.pacientes.getOrDefault(internado.expediente(), Map.of());
            Map<String, Object> nota = intelisis.ultimaIndicacion.getOrDefault(internado.idIngreso(), Map.of());
            // La dieta vigente es la que tiene enfermería; si no hay, la de la última indicación.
            String[] dieta = intelisis.dietas.getOrDefault(internado.idIngreso(),
                    new String[]{(String) nota.get("desayuno"), (String) nota.get("comida"), (String) nota.get("cena")});
            LocalDate nacimiento = paciente.get("nacimiento") instanceof Timestamp t ? t.toLocalDateTime().toLocalDate() : null;
            hojas.add(new HojaIndicacionesResponse(internado.idIngreso(), internado.paciente(), (String) paciente.get("sexo"),
                    edad(nacimiento, ahora.toLocalDate()), nacimiento, internado.expediente(), internado.espacio(), central, ahora,
                    intelisis.generales.getOrDefault(internado.idIngreso(), List.of()), porHorario, porInfusion,
                    dieta[0], dieta[1], dieta[2],
                    intelisis.medicos.get((String) nota.get("tratante")), intelisis.medicos.get((String) nota.get("adscrito")),
                    intelisis.disponible));
        }
        return hojas;
    }

    /** "27 Años, 2 Meses, 20 Días", como la hoja de Intelisis. */
    static String edad(LocalDate nacimiento, LocalDate hoy) {
        if (nacimiento == null) {
            return null;
        }
        Period p = Period.between(nacimiento, hoy);
        return p.getYears() + " Años, " + p.getMonths() + " Meses, " + p.getDays() + " Días";
    }

    private static final class DeIntelisis {
        boolean disponible;
        final Map<String, Map<String, Object>> pacientes = new HashMap<>();
        final Map<Integer, List<String>> generales = new HashMap<>();
        final Map<Integer, Map<String, Object>> ultimaIndicacion = new HashMap<>();
        final Map<String, MedicoHojaResponse> medicos = new HashMap<>();
        // Desayuno, comida y cena vigentes (vsa_EnfDieta), por ingreso.
        final Map<Integer, String[]> dietas = new HashMap<>();
    }

    /** Lo que no se guarda en HAERO, leído en el momento (solo SELECT). Si falla, la hoja sale sin eso. */
    private DeIntelisis leerDeIntelisis(List<ServicioCarrito.Internado> internados) {
        DeIntelisis datos = new DeIntelisis();
        Optional<JdbcTemplate> plantilla = conexion.plantilla();
        if (plantilla.isEmpty()) {
            return datos;
        }
        JdbcTemplate intelisis = plantilla.get();
        try {
            List<Object> ingresos = internados.stream().map(i -> (Object) i.idIngreso()).toList();
            List<Object> expedientes = internados.stream().map(ServicioCarrito.Internado::expediente).filter(e -> e != null).map(e -> (Object) e).toList();
            if (!expedientes.isEmpty()) {
                intelisis.query("SELECT Cliente, Sexo, FechaNacimiento FROM Cte WITH (NOLOCK) WHERE Cliente IN (" + marcadores(expedientes.size()) + ")",
                        rs -> {
                            Map<String, Object> p = new HashMap<>();
                            p.put("sexo", limpio(rs.getString(2)));
                            p.put("nacimiento", rs.getTimestamp(3));
                            datos.pacientes.put(limpio(rs.getString(1)), p);
                        }, expedientes.toArray());
            }
            intelisis.query("""
                    SELECT g.ID_IngHosp, g.Indicacion, g.Instruccion, g.Nota FROM vsa_IndMedicaGeneral g WITH (NOLOCK)
                    WHERE g.ID_IngHosp IN (""" + marcadores(ingresos.size()) + """
                    ) AND ISNULL(g.Suspendida, 0) = 0 ORDER BY g.Fecha, g.ID_IndMedicaGenral
                    """, rs -> {
                StringBuilder texto = new StringBuilder(Optional.ofNullable(limpio(rs.getString(2))).orElse(""));
                if (limpio(rs.getString(3)) != null) texto.append(texto.isEmpty() ? "" : ": ").append(limpio(rs.getString(3)));
                if (limpio(rs.getString(4)) != null) texto.append(" ").append(limpio(rs.getString(4)));
                if (!texto.isEmpty()) {
                    datos.generales.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(texto.toString().strip());
                }
            }, ingresos.toArray());
            List<String> usuarios = new ArrayList<>();
            intelisis.query("""
                    SELECT ID_IngHosp, DesayunoTipo, ComidaTipo, CenaTipo, Usuario, MedicoAdscrito FROM (
                        SELECT m.*, ROW_NUMBER() OVER (PARTITION BY m.ID_IngHosp ORDER BY m.Fecha DESC, m.ID_IndMedica DESC) AS orden
                        FROM vsa_IndMedica m WITH (NOLOCK) WHERE m.ID_IngHosp IN (""" + marcadores(ingresos.size()) + """
                    )) x WHERE x.orden = 1
                    """, rs -> {
                Map<String, Object> n = new HashMap<>();
                n.put("desayuno", limpio(rs.getString(2)));
                n.put("comida", limpio(rs.getString(3)));
                n.put("cena", limpio(rs.getString(4)));
                n.put("tratante", limpio(rs.getString(5)));
                n.put("adscrito", limpio(rs.getString(6)));
                datos.ultimaIndicacion.put(rs.getInt(1), n);
                if (n.get("tratante") != null) usuarios.add((String) n.get("tratante"));
                if (n.get("adscrito") != null) usuarios.add((String) n.get("adscrito"));
            }, ingresos.toArray());
            // El médico solo escribe la dieta cuando la cambia (DietaMovimiento); la vigente queda en
            // vsa_EnfDieta, una "Activa" por paciente. En ayuno se imprime AYUNO en los tres tiempos.
            intelisis.query("""
                    SELECT ID_IngHosp, DesayunoTipo, ComidaTipo, CenaTipo, Ayuno FROM (
                        SELECT e.*, ROW_NUMBER() OVER (PARTITION BY e.ID_IngHosp ORDER BY e.Fecha DESC, e.ID_EnfDieta DESC) AS orden
                        FROM vsa_EnfDieta e WITH (NOLOCK) WHERE e.ID_IngHosp IN (""" + marcadores(ingresos.size()) + """
                        ) AND e.Estatus = 'Activa' AND ISNULL(e.Suspendida, 0) = 0
                    ) x WHERE x.orden = 1
                    """, rs -> {
                String[] dieta = rs.getBoolean(5)
                        ? new String[]{"AYUNO", "AYUNO", "AYUNO"}
                        : new String[]{limpio(rs.getString(2)), limpio(rs.getString(3)), limpio(rs.getString(4))};
                datos.dietas.put(rs.getInt(1), dieta);
            }, ingresos.toArray());
            if (!usuarios.isEmpty()) {
                intelisis.query(ServicioSincronizacionHospitalizacion.SQL_MEDICOS + "(" + marcadores(usuarios.size()) + ")", rs -> {
                    datos.medicos.put(limpio(rs.getString(1)), new MedicoHojaResponse(limpio(rs.getString(2)), limpio(rs.getString(3)), limpio(rs.getString(4))));
                }, usuarios.toArray());
            }
            datos.disponible = true;
        } catch (Exception e) {
            log.warn("Hoja de indicaciones sin datos de Intelisis: {}", e.getMessage());
            datos.disponible = false;
        }
        return datos;
    }

    // ---------- Terminar carrito y hoja de baja ----------

    /**
     * Termina el carrito del área: si algo que se metió se canceló después, pide confirmación
     * (o que se saque); asigna el folio de HAERO a la hoja de baja de cada paciente con algo
     * surtido hoy y regresa las hojas para imprimir.
     */
    @Transactional
    public List<HojaBajaResponse> terminar(String central, boolean confirmarCancelados, String nombreUsuario) {
        CarritoResponse actual = carrito.carrito(central);
        List<String> cancelados = actual.getCamas().stream()
                .flatMap(c -> c.getLineas().stream().filter(l -> "SUSPENDIDO".equals(l.getEstado()))
                        .map(l -> "cama " + c.getEspacio() + ": " + l.getMedicamento()))
                .toList();
        if (!cancelados.isEmpty() && !confirmarCancelados) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Se cancelaron medicamentos que ya están en el carrito ("
                    + String.join("; ", cancelados) + "). Sácalos del carrito o confirma para terminar así.");
        }
        long usuario = inventario.usuario(nombreUsuario).getId();
        LocalDate hoy = LocalDate.now();
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        for (var cama : actual.getCamas()) {
            boolean conSurtido = cama.getLineas().stream().mapToInt(LineaCarrito::getEntregadas).sum() > 0;
            if (!conSurtido) {
                continue;
            }
            Long receta = haero.queryForList("SELECT id FROM farmacia_receta WHERE folio = ?", Long.class,
                    ServicioCarrito.folio(hoy, cama.getIdIngreso())).stream().findFirst().orElse(null);
            if (receta != null && haero.queryForObject("SELECT COUNT(*) FROM farmacia_hoja_baja WHERE receta_id = ?", Integer.class, receta) == 0) {
                haero.update("INSERT INTO farmacia_hoja_baja (receta_id, dia, central, espacio, fecha, usuario_id) VALUES (?, ?, ?, ?, ?, ?)",
                        receta, java.sql.Date.valueOf(hoy), central, cama.getEspacio(), ahora, usuario);
            }
        }
        carrito.registrarCierre(central, usuario);
        return hojasBaja(central, hoy, null);
    }

    /** Las hojas de baja del área en el día (o la de un paciente), con lo que realmente salió. */
    public List<HojaBajaResponse> hojasBaja(String central, LocalDate dia, Integer idIngreso) {
        List<Object> parametros = new ArrayList<>(List.of(java.sql.Date.valueOf(dia), central));
        String filtro = "";
        if (idIngreso != null) {
            filtro = " AND r.folio = ?";
            parametros.add(ServicioCarrito.folio(dia, idIngreso));
        }
        List<Map<String, Object>> hojas = haero.queryForList("""
                SELECT h.folio, h.dia, h.fecha, h.espacio, h.central, r.id AS receta, r.paciente_nombre, r.paciente_expediente, u.nombre_completo
                FROM farmacia_hoja_baja h JOIN farmacia_receta r ON r.id = h.receta_id JOIN usuario u ON u.id = h.usuario_id
                WHERE h.dia = ? AND h.central = ?""" + filtro + " ORDER BY h.folio", parametros.toArray());
        if (hojas.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Object[]>> partidasPorReceta = new HashMap<>();
        List<Object> recetas = hojas.stream().map(h -> h.get("receta")).toList();
        haero.query("""
                SELECT r.id, rc.medicamento_clave, fl.numero_lote, fl.caducidad, SUM(p.piezas - p.piezas_devueltas)
                FROM farmacia_entrega_partida p
                JOIN farmacia_entrega e ON e.id = p.entrega_id
                JOIN farmacia_receta r ON r.id = e.receta_id
                JOIN farmacia_receta_clave rc ON rc.id = p.receta_clave_id
                JOIN farmacia_lote fl ON fl.id = p.farmacia_lote_id
                WHERE r.id IN (""" + marcadores(recetas.size()) + """
                ) GROUP BY r.id, rc.medicamento_clave, fl.numero_lote, fl.caducidad
                HAVING SUM(p.piezas - p.piezas_devueltas) > 0
                ORDER BY rc.medicamento_clave
                """, rs -> {
            partidasPorReceta.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Object[]{rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate(), rs.getInt(5)});
        }, recetas.toArray());

        // Descripción y unidad como en Intelisis (clave "-1"), del espejo; precio leído en vivo.
        List<String> articulos = partidasPorReceta.values().stream().flatMap(List::stream)
                .map(p -> p[0] + ServicioCortes.SUFIJO_FARMACIA).distinct().toList();
        Map<String, String[]> descripcion = new HashMap<>();
        if (!articulos.isEmpty()) {
            haero.query("SELECT articulo, descripcion, unidad FROM intelisis_articulo WHERE articulo IN (" + marcadores(articulos.size()) + ")",
                    rs -> {
                        descripcion.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)});
                    }, articulos.toArray());
        }
        Map<String, BigDecimal> precios = precios(articulos);

        List<HojaBajaResponse> respuesta = new ArrayList<>();
        for (Map<String, Object> h : hojas) {
            List<PartidaBajaResponse> partidas = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            int piezas = 0;
            for (Object[] p : partidasPorReceta.getOrDefault(((Number) h.get("receta")).longValue(), List.of())) {
                String articulo = p[0] + ServicioCortes.SUFIJO_FARMACIA;
                int cantidad = (int) p[3];
                BigDecimal precio = precios.get(articulo);
                BigDecimal importe = precio == null ? null : precio.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
                String[] d = descripcion.getOrDefault(articulo, new String[]{null, null});
                partidas.add(new PartidaBajaResponse(articulo, d[0] != null ? d[0] : (String) p[0], cantidad, (String) p[1], (LocalDate) p[2],
                        precio, importe, d[1]));
                piezas += cantidad;
                if (importe != null) total = total.add(importe);
            }
            LocalDate diaHoja = ((java.sql.Date) h.get("dia")).toLocalDate();
            int folio = ((Number) h.get("folio")).intValue();
            respuesta.add(new HojaBajaResponse(folio, diaHoja, ((Timestamp) h.get("fecha")).toLocalDateTime(), (String) h.get("paciente_nombre"),
                    (String) h.get("paciente_expediente"), (String) h.get("espacio"), (String) h.get("central"),
                    String.format("%06d", folio) + " " + diaHoja.format(OBSERVACIONES), partidas, piezas,
                    precios.isEmpty() ? null : total, (String) h.get("nombre_completo"), !precios.isEmpty()));
        }
        return respuesta;
    }

    /**
     * Costo por pieza de Intelisis: ArtCosto.CostoPromedio de la clave "-1" en la empresa y sucursal
     * de Farmacia (validado: es el costo con que se captura el "Consumo Farmacia"). Vacío si no hay conexión.
     */
    private Map<String, BigDecimal> precios(List<String> articulos) {
        Map<String, BigDecimal> precios = new HashMap<>();
        Optional<JdbcTemplate> plantilla = conexion.plantilla();
        if (articulos.isEmpty() || plantilla.isEmpty()) {
            return precios;
        }
        try {
            List<Object> parametros = new ArrayList<>(List.of(empresa, sucursal));
            parametros.addAll(articulos);
            plantilla.get().query("SELECT Articulo, CostoPromedio FROM ArtCosto WITH (NOLOCK) WHERE Empresa = ? AND Sucursal = ? AND Articulo IN ("
                    + marcadores(articulos.size()) + ")", rs -> {
                BigDecimal costo = rs.getBigDecimal(2);
                if (costo != null) precios.put(limpio(rs.getString(1)), costo.setScale(2, RoundingMode.HALF_UP));
            }, parametros.toArray());
        } catch (Exception e) {
            log.warn("Hoja de baja sin precios de Intelisis: {}", e.getMessage());
        }
        return precios;
    }

    private static String marcadores(int cantidad) {
        return String.join(", ", java.util.Collections.nCopies(cantidad, "?"));
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
