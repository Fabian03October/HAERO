package com.example.backend.farmacia.service;

import com.example.backend.intelisis.config.ConexionIntelisis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fuente de financiamiento de un lote (lógica del script de Ada, almacén): con qué Donación FONSABI / Entrada UNOPS
 * llegó, su orden de suministro, remisión, contrato, fuente (SAEAC, SADMI, SFAS, SAEPGC) y PDF, y a qué entrada
 * corresponde cada consumo de Farmacia por PEPS. Solo lee Intelisis.
 *
 * Una entrada llega de tres formas: directa (al artículo unidosis "-1"), por Conversión (la donación entra por caja y una
 * "Conversion" la pasa al -1; su Referencia dice "DONACION FONSABI 953" o "ENTRADA UNOPS 4079") o por Cambio de lote
 * (almacén corrigió el lote en "Cambio Serie/Lote": la donación quedó con el lote viejo y Farmacia surte el nuevo).
 */
@Service
public class ServicioFinanciamientoIntelisis {

    private static final Logger log = LoggerFactory.getLogger(ServicioFinanciamientoIntelisis.class);

    private final ConexionIntelisis conexion;
    private final List<String> movimientosConsumo;

    public ServicioFinanciamientoIntelisis(ConexionIntelisis conexion,
                                           @Value("${intelisis.farmacia.movimientos-consumo:Consumo Farmacia}") String movimientos) {
        this.conexion = conexion;
        this.movimientosConsumo = Arrays.stream(movimientos.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    /** Una entrada FONSABI/UNOPS del lote con sus datos y el rango de piezas que cubre (PEPS). */
    public record Entrada(String articulo, String lote, long idDon, String entrada, String via, LocalDate fechaLlegada,
                          BigDecimal cantidad, int piezaDesde, int piezaHasta, LocalDate caducidad, String fuente,
                          String ordenSuministro, String remision, String origenRemision, String contrato, String proveedor,
                          String concepto, BigDecimal costo, String documentoPdf, boolean entregaMismoDia, String cartaCanje,
                          String tipoProceso, String tipoRed, String destino, String fechaLimiteEntrega) {
    }

    /** entradas del lote y, por consumo ("Mov MovID|clave|lote"), la entrada que le toca. */
    public record Resultado(List<Entrada> entradas, Map<String, Entrada> porConsumo, String aviso) {
        static Resultado sinDatos(String aviso) {
            return new Resultado(List.of(), Map.of(), aviso);
        }
    }

    // ---------- lectura de Intelisis ----------

    private static final String ENTRADAS_SQL = """
            WITH mov AS (
                SELECT se.Articulo, se.SerieLote, e.ID, e.Mov, e.MovID, e.Concepto, e.Referencia, e.Observaciones,
                       e.FechaEmision, e.FechaRegistro, se.RenglonID, se.Cantidad, se.FechaElaboracion, se.FechaCaducidad
                FROM SerieLoteMov se WITH (NOLOCK) JOIN Inv e WITH (NOLOCK) ON e.ID = se.ID
                WHERE se.Modulo = 'INV' AND e.Estatus = 'CONCLUIDO' AND e.Mov IN ('Donacion FONSABI', 'Entrada UNOPS', 'Conversion')
                  AND {LOTE_SE} {CLAVE_SE}
            )
            SELECT m.Articulo, m.SerieLote, m.ID, m.Mov, m.MovID, m.Concepto, m.Referencia, m.Observaciones, m.FechaEmision,
                   m.FechaRegistro, ABS(m.Cantidad), ed.Costo, m.FechaElaboracion, m.FechaCaducidad, CAST(NULL AS VARCHAR(60))
            FROM mov m JOIN InvD ed WITH (NOLOCK) ON ed.ID = m.ID AND ed.RenglonID = m.RenglonID
            WHERE m.Mov IN ('Donacion FONSABI', 'Entrada UNOPS')
            UNION ALL
            SELECT m.Articulo, m.SerieLote, dn.ID, dn.Mov, dn.MovID, dn.Concepto, dn.Referencia, dn.Observaciones, dn.FechaEmision,
                   dn.FechaRegistro, ABS(m.Cantidad), dc.Costo, m.FechaElaboracion, m.FechaCaducidad, CAST('Conversion ' + m.MovID AS VARCHAR(60))
            FROM mov m
            CROSS APPLY (
                SELECT CASE WHEN LTRIM(m.Referencia) LIKE 'DONACION FONSABI %' THEN 'Donacion FONSABI'
                            WHEN LTRIM(m.Referencia) LIKE 'ENTRADA UNOPS %' THEN 'Entrada UNOPS' END AS movRef,
                       LTRIM(RTRIM(SUBSTRING(LTRIM(m.Referencia), CASE WHEN LTRIM(m.Referencia) LIKE 'DONACION FONSABI %' THEN 18 ELSE 15 END, 20))) AS movIdRef
            ) r
            JOIN Inv dn WITH (NOLOCK) ON dn.Mov = r.movRef AND dn.MovID = r.movIdRef AND dn.Estatus = 'CONCLUIDO'
            OUTER APPLY (SELECT MAX(x.Costo) AS Costo FROM InvD x WITH (NOLOCK)
                         WHERE x.ID = dn.ID AND (x.Articulo = m.Articulo OR x.Articulo + '-1' = m.Articulo)) dc
            WHERE m.Mov = 'Conversion' AND r.movRef IS NOT NULL
            UNION ALL
            SELECT h.Articulo, h.SerieLoteNuevo, e.ID, e.Mov, e.MovID, e.Concepto, e.Referencia, e.Observaciones, e.FechaEmision,
                   e.FechaRegistro, ABS(se.Cantidad), ed.Costo, se.FechaElaboracion, COALESCE(h.FechaCaducidadNueva, se.FechaCaducidad),
                   CAST(LEFT('Cambio de lote ' + h.SerieLote, 60) AS VARCHAR(60))
            FROM CambioSerieLoteHistorico h WITH (NOLOCK)
            JOIN SerieLoteMov se WITH (NOLOCK) ON se.Modulo = 'INV' AND se.Articulo = h.Articulo AND se.SerieLote = h.SerieLote
            JOIN Inv e WITH (NOLOCK) ON e.ID = se.ID AND e.Estatus = 'CONCLUIDO' AND e.Mov IN ('Donacion FONSABI', 'Entrada UNOPS')
            JOIN InvD ed WITH (NOLOCK) ON ed.ID = se.ID AND ed.RenglonID = se.RenglonID
            WHERE h.SerieLoteNuevo IS NOT NULL AND h.SerieLoteNuevo <> h.SerieLote AND {LOTE_H} {CLAVE_H}
            """;

    /** Entradas, consumos y su reparto PEPS del lote (clave opcional: base, sin "-1"). */
    public Resultado delLote(String lote, String clave) {
        JdbcTemplate intelisis = conexion.plantilla().orElse(null);
        if (intelisis == null) {
            return Resultado.sinDatos("Sin conexión con Intelisis: no se muestra la fuente de financiamiento. " + conexion.motivoNoDisponible());
        }
        try {
            List<Object> p = new ArrayList<>();
            String sql = ENTRADAS_SQL
                    .replace("{LOTE_SE}", filtroLote("se.SerieLote", lote, p))
                    .replace("{CLAVE_SE}", filtroClave("se.Articulo", clave, p));
            // Los "?" del segundo bloque van después de los del primero (mismo orden en que aparecen).
            sql = sql.replace("{LOTE_H}", filtroLote("h.SerieLoteNuevo", lote, p)).replace("{CLAVE_H}", filtroClave("h.Articulo", clave, p));
            List<Movimiento> movimientos = intelisis.query(sql, (rs, n) -> new Movimiento(limpio(rs.getString(1)), limpio(rs.getString(2)),
                    rs.getLong(3), limpio(rs.getString(4)), limpio(rs.getString(5)), limpio(rs.getString(6)), limpio(rs.getString(7)),
                    limpio(rs.getString(8)), fecha(rs.getTimestamp(9)), fecha(rs.getTimestamp(10)), cero(rs.getBigDecimal(11)),
                    rs.getBigDecimal(12), dia(rs.getTimestamp(13)), dia(rs.getTimestamp(14)), limpio(rs.getString(15))), p.toArray());
            if (movimientos.isEmpty()) {
                return Resultado.sinDatos(null);
            }
            List<Long> ids = movimientos.stream().map(Movimiento::idDon).distinct().toList();
            Map<Long, Map<String, String>> caracteristicas = caracteristicas(intelisis, ids);
            Map<Long, List<String[]>> anexos = anexos(intelisis, ids);

            List<Object> pc = new ArrayList<>(movimientosConsumo);
            String sqlConsumos = "SELECT se.Articulo, se.SerieLote, e.ID, se.RenglonID, e.Mov, e.MovID, ABS(se.Cantidad) "
                    + "FROM SerieLoteMov se WITH (NOLOCK) JOIN Inv e WITH (NOLOCK) ON e.ID = se.ID "
                    + "WHERE se.Modulo = 'INV' AND e.Estatus = 'CONCLUIDO' AND e.Mov IN ("
                    + movimientosConsumo.stream().map(x -> "CAST(? AS VARCHAR(30))").collect(Collectors.joining(", ")) + ") AND "
                    + filtroLote("se.SerieLote", lote, pc) + " " + filtroClave("se.Articulo", clave, pc)
                    + " ORDER BY e.FechaEmision, e.ID, se.RenglonID";
            List<Consumo> consumos = intelisis.query(sqlConsumos, (rs, n) -> new Consumo(limpio(rs.getString(1)), limpio(rs.getString(2)),
                    limpio(rs.getString(5)) + (limpio(rs.getString(6)) != null ? " " + limpio(rs.getString(6)) : ""), cero(rs.getBigDecimal(7))), pc.toArray());

            List<Entrada> entradas = armarEntradas(movimientos, caracteristicas, anexos);
            return new Resultado(entradas, repartirPeps(entradas, consumos), null);
        } catch (DataAccessException e) {
            log.warn("No se pudo leer la fuente de financiamiento del lote en Intelisis: {}", e.getMostSpecificCause().getMessage());
            return Resultado.sinDatos("Intelisis no respondió: no se muestra la fuente de financiamiento del lote.");
        }
    }

    private static String filtroLote(String columna, String lote, List<Object> p) {
        p.add(lote);
        p.add(lote.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "[ /]%");
        return "(" + columna + " = CAST(? AS VARCHAR(50)) OR " + columna + " LIKE CAST(? AS VARCHAR(60)))";
    }

    private static String filtroClave(String columna, String clave, List<Object> p) {
        if (clave == null) return "";
        p.add(clave);
        p.add(clave + "-1");
        return "AND " + columna + " IN (CAST(? AS VARCHAR(20)), CAST(? AS VARCHAR(20)))";
    }

    /** Pestaña "Características" (forma UNOPS) de cada donación: campo → valor, con el nombre del campo sin acentos. */
    private static Map<Long, Map<String, String>> caracteristicas(JdbcTemplate intelisis, List<Long> ids) {
        Map<Long, Map<String, String>> resultado = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            List<Long> grupo = ids.subList(i, Math.min(i + 1000, ids.size()));
            intelisis.query("SELECT v.AplicaClave, v.Campo, v.Valor FROM FormaExtraValor v WITH (NOLOCK) "
                    + "WHERE v.FormaTipo = 'UNOPS' AND v.Aplica = 'Movimiento' AND ISNULL(v.Eliminado, 0) = 0 AND v.AplicaClave IN ("
                    + grupo.stream().map(x -> "CAST(? AS VARCHAR(30))").collect(Collectors.joining(", ")) + ")", rs -> {
                String clave = limpio(rs.getString(1));
                String valor = limpio(rs.getString(3));
                if (clave == null || valor == null || !clave.startsWith("INV")) return;
                try {
                    resultado.computeIfAbsent(Long.parseLong(clave.substring(3)), k -> new HashMap<>())
                            .merge(sinAcentos(rs.getString(2)), valor, (a, b) -> a.compareTo(b) >= 0 ? a : b);
                } catch (NumberFormatException ignorar) {
                    // AplicaClave que no es "INV" + número: no es de una donación.
                }
            }, grupo.stream().map(id -> (Object) ("INV" + id)).toArray());
        }
        return resultado;
    }

    /** Anexos de cada donación en orden: [Nombre, Direccion]. */
    private static Map<Long, List<String[]>> anexos(JdbcTemplate intelisis, List<Long> ids) {
        Map<Long, List<String[]>> resultado = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 1000) {
            List<Long> grupo = ids.subList(i, Math.min(i + 1000, ids.size()));
            intelisis.query("SELECT a.ID, a.Nombre, a.Direccion FROM AnexoMov a WITH (NOLOCK) WHERE a.Rama = 'INV' AND a.ID IN ("
                    + grupo.stream().map(x -> "?").collect(Collectors.joining(", ")) + ") ORDER BY a.ID, a.IDR", rs -> {
                resultado.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new String[]{limpio(rs.getString(2)), limpio(rs.getString(3))});
            }, grupo.toArray());
        }
        return resultado;
    }

    // ---------- lógica del script (sin base de datos, para poder probarla) ----------

    /** Una fila de entrada leída de Intelisis (directa, por conversión o por cambio de lote). */
    record Movimiento(String articulo, String lote, long idDon, String mov, String movId, String concepto, String referencia,
                      String observaciones, LocalDateTime fechaLlegada, LocalDateTime fechaRegistro, BigDecimal cantidad,
                      BigDecimal costo, LocalDate fabricacion, LocalDate caducidad, String via) {
    }

    record Consumo(String articulo, String lote, String folio, BigDecimal cantidad) {
    }

    /**
     * Junta las filas por entrada (como el GROUP BY del script), le pone sus datos y el rango de piezas PEPS: por lote,
     * primero la que caduca antes, luego la que llegó antes (día) y, el mismo día, el orden de captura.
     */
    static List<Entrada> armarEntradas(List<Movimiento> movimientos, Map<Long, Map<String, String>> caracteristicas,
                                       Map<Long, List<String[]>> anexos) {
        Map<List<Object>, List<Movimiento>> grupos = new LinkedHashMap<>();
        for (Movimiento m : movimientos) {
            grupos.computeIfAbsent(Arrays.asList(m.articulo(), m.lote(), m.idDon(), m.mov(), m.movId(), m.via()), k -> new ArrayList<>()).add(m);
        }
        record Junta(Movimiento m, BigDecimal cantidad, BigDecimal costo, LocalDate fabricacion, LocalDate caducidad) {
        }
        List<Junta> juntas = grupos.values().stream().map(g -> new Junta(g.get(0),
                g.stream().map(Movimiento::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add),
                g.stream().map(Movimiento::costo).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                g.stream().map(Movimiento::fabricacion).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                g.stream().map(Movimiento::caducidad).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null))).toList();

        Comparator<Junta> peps = Comparator
                .comparing((Junta j) -> j.caducidad(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(j -> j.m().fechaLlegada() == null ? null : j.m().fechaLlegada().toLocalDate(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(j -> j.m().fechaRegistro(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(j -> j.m().idDon());
        Map<String, List<Junta>> porLote = juntas.stream().sorted(peps)
                .collect(Collectors.groupingBy(j -> j.m().articulo() + "|" + j.m().lote(), LinkedHashMap::new, Collectors.toList()));

        List<Entrada> resultado = new ArrayList<>();
        for (List<Junta> delLote : porLote.values()) {
            int acumulado = 0;
            for (Junta j : delLote) {
                Movimiento m = j.m();
                LocalDate llegada = m.fechaLlegada() == null ? null : m.fechaLlegada().toLocalDate();
                long mismoDia = delLote.stream().filter(o -> Objects.equals(o.caducidad(), j.caducidad())
                        && Objects.equals(o.m().fechaLlegada() == null ? null : o.m().fechaLlegada().toLocalDate(), llegada)).count();
                int desde = acumulado + 1;
                acumulado += j.cantidad().intValue();
                Map<String, String> ux = caracteristicas.getOrDefault(m.idDon(), Map.of());
                List<String[]> anexosDon = anexos.getOrDefault(m.idDon(), List.of());
                String anexoNombre = anexosDon.stream().map(a -> a[0]).filter(n -> n != null && n.contains("-")).findFirst().orElse(null);
                String pdf = anexosDon.stream().map(a -> a[1]).filter(d -> d != null && d.toLowerCase(Locale.ROOT).endsWith(".pdf")).findFirst().orElse(null);
                Datos d = datos(m.referencia(), m.observaciones(), anexoNombre, ux);
                resultado.add(new Entrada(m.articulo(), m.lote(), m.idDon(), m.mov() + " " + m.movId(), m.via(), llegada, j.cantidad(),
                        desde, acumulado, j.caducidad(), fuente(d.ordenSuministro(), m.lote()), d.ordenSuministro(), d.remision(),
                        d.origenRemision(), ux.get("CONTRATO"), m.observaciones(), m.concepto(), j.costo(), pdf, mismoDia > 1,
                        cartaCanje(m.lote(), j.caducidad(), llegada), ux.get("TIPO DE PROCESO"), ux.get("TIPO DE RED"),
                        ux.get("DESTINO"), ux.get("FECHA LIMITE DE ENTREGA")));
            }
        }
        return resultado;
    }

    /** Cada consumo, a la entrada en cuyo rango cae su primera pieza; si se consumió más de lo que entró, a la última. */
    static Map<String, Entrada> repartirPeps(List<Entrada> entradas, List<Consumo> consumos) {
        Map<String, List<Entrada>> porLote = entradas.stream().collect(Collectors.groupingBy(e -> e.articulo() + "|" + e.lote()));
        Map<String, Integer> consumidas = new HashMap<>();
        Map<String, Entrada> resultado = new LinkedHashMap<>();
        for (Consumo c : consumos) {
            String llave = c.articulo() + "|" + c.lote();
            int antes = consumidas.getOrDefault(llave, 0);
            consumidas.put(llave, antes + c.cantidad().intValue());
            List<Entrada> delLote = porLote.get(llave);
            if (delLote == null || delLote.isEmpty()) continue;
            Entrada toca = delLote.stream().filter(e -> e.piezaHasta() > antes).min(Comparator.comparingInt(Entrada::piezaHasta))
                    .orElse(delLote.stream().max(Comparator.comparingInt(Entrada::piezaHasta)).get());
            resultado.putIfAbsent(llaveConsumo(c.folio(), ServicioTrazabilidad.claveBase(c.articulo()), ServicioTrazabilidad.loteBase(c.lote())), toca);
        }
        return resultado;
    }

    /** Llave para unir un consumo con la entrega de Intelisis que muestra Trazabilidad. */
    public static String llaveConsumo(String folio, String clave, String lote) {
        return folio + "|" + clave + "|" + lote;
    }

    record Datos(String ordenSuministro, String remision, String origenRemision) {
    }

    /** Orden de suministro y remisión: de Características; si no, del nombre del anexo o de la Referencia ("REM:"). */
    static Datos datos(String referencia, String proveedor, String anexoNombre, Map<String, String> ux) {
        String ref = referencia == null ? null : referencia.strip();
        String remArchivo = null;
        if (anexoNombre != null) {
            int guion = anexoNombre.lastIndexOf('-');
            String cola = guion >= 0 ? anexoNombre.substring(guion + 1) : "";
            if (cola.length() >= 7 && cola.chars().allMatch(Character::isDigit)) remArchivo = cola;
        }
        boolean refEsNumero = ref != null && ref.length() >= 6 && ref.chars().allMatch(Character::isDigit);
        String base = anexoNombre == null ? null : remArchivo != null ? anexoNombre.substring(0, anexoNombre.length() - remArchivo.length() - 1) : anexoNombre;
        String ordenEnArchivo = base;
        if (base != null) {
            int bajo = base.indexOf('_');
            int guion = base.indexOf('-');
            if (bajo >= 0 && guion >= 0 && bajo < guion) ordenEnArchivo = base.substring(guion + 1);
        }
        String orden = ux.get("ORDEN DE REPOSICION");
        if (orden == null) {
            orden = !refEsNumero ? ref : ordenEnArchivo != null && ordenEnArchivo.matches(".*-20\\d\\d-.*") ? ordenEnArchivo : null;
        }
        String remCaract = ux.get("NUMERO DE REMISION");
        String origen = remCaract != null ? "CARACTERISTICAS" : remArchivo != null ? "ARCHIVO"
                : refEsNumero || contiene(ref, "REM:") || contiene(proveedor, "REM:") ? "REFERENCIA" : null;
        String remision = remCaract != null ? remCaract : remArchivo != null ? remArchivo : refEsNumero ? ref
                : contiene(ref, "REM:") ? despuesDeRem(ref) : contiene(proveedor, "REM:") ? despuesDeRem(proveedor) : null;
        return new Datos(orden, remision, origen);
    }

    /** SAEPGC, SAEAC, SADMI o SFAS según la orden de suministro y, si no dice, el lote. */
    static String fuente(String ordenSuministro, String lote) {
        String deOrden = fuenteDe(ordenSuministro);
        if (deOrden != null) return deOrden;
        String deLote = fuenteDe(lote);
        return deLote != null ? deLote : "SIN FUENTE";
    }

    private static String fuenteDe(String texto) {
        if (texto == null) return null;
        String t = texto.toUpperCase(Locale.ROOT);
        if (t.contains("SAEPG") || t.contains("SAPG") || t.contains("SEPGC")) return "SAEPGC";
        if (t.contains("SAEAC") || t.contains("SEAC") || t.contains("SAEEAC") || t.contains("SEAEC")) return "SAEAC";
        if (t.contains("SADMI")) return "SADMI";
        if (t.contains("SFAS")) return "SFAS";
        if (t.contains("E003")) return "SAEAC";
        return null;
    }

    /** Carta canje: lo que diga el lote; si no, más de 12 meses de vida al llegar = SCC. */
    static String cartaCanje(String lote, LocalDate caducidad, LocalDate llegada) {
        String l = lote == null ? "" : lote.toUpperCase(Locale.ROOT);
        if (l.contains("CCC") || l.contains("C.C.C")) return "CCC";
        if (l.contains("SCC") || l.contains("S.C.C")) return "SCC";
        if (caducidad == null || llegada == null) return null;
        return caducidad.isAfter(llegada.plusMonths(12)) ? "SCC" : "CCC";
    }

    private static boolean contiene(String texto, String buscado) {
        return texto != null && texto.toUpperCase(Locale.ROOT).contains(buscado);
    }

    private static String despuesDeRem(String texto) {
        int i = texto.toUpperCase(Locale.ROOT).indexOf("REM:");
        String resto = texto.substring(i + 4, Math.min(texto.length(), i + 4 + 30)).stripLeading();
        int espacio = resto.indexOf(' ');
        String valor = espacio >= 0 ? resto.substring(0, espacio) : resto;
        return valor.isEmpty() ? null : valor;
    }

    static String sinAcentos(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").strip().toUpperCase(Locale.ROOT);
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.replace('\t', ' ').strip();
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static LocalDateTime fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static LocalDate dia(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }
}
