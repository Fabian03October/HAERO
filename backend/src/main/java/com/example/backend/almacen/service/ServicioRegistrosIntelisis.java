package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.RegistroIntelisisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registros de Intelisis que se pueden elegir en HU10 (entrada) y HU21 (alta inicial).
 * Lee solo la copia de consulta (tablas intelisis_*), nunca Intelisis directamente.
 *
 *  - ENTRADA: lo que Intelisis registró como entrada al almacén de medicamentos en los
 *    últimos días: compras ("Ent. Compra Farmacia", con su pedido y contrato) y
 *    donaciones o traspasos (Inv).
 *  - ALTA_INICIAL: la existencia actual del almacén por lote.
 *
 * Unidades: la clave base de Intelisis (sin "-1") viene en cajas y la clave "-1" en
 * piezas; las piezas se pasan a cajas con las piezas por caja del catálogo.
 */
@Service
public class ServicioRegistrosIntelisis {

    public static final String ENTRADA = "ENTRADA";
    public static final String ALTA_INICIAL = "ALTA_INICIAL";

    private static final String MOV_RECEPCION = "Ent. Compra Farmacia";

    private final JdbcTemplate haero;
    private final String almacenPrincipal;
    private final int diasEntradas;

    public ServicioRegistrosIntelisis(JdbcTemplate haero,
                                      @Value("${intelisis.almacenes:25301,25301-C}") String almacenes,
                                      @Value("${almacen.entradas.dias:90}") int diasEntradas) {
        this.haero = haero;
        this.almacenPrincipal = almacenes.split(",")[0].trim();
        this.diasEntradas = diasEntradas;
    }

    public List<RegistroIntelisisResponse> listar(String clave, String tipo) {
        if (clave == null || clave.isBlank()) {
            return List.of();
        }
        String limpia = clave.strip();
        Integer piezasPorCaja = piezasPorCaja(limpia);
        List<LoteHaero> lotesHaero = lotesHaero(limpia);

        List<RegistroIntelisisResponse> registros = new ArrayList<>();
        if (ALTA_INICIAL.equals(tipo)) {
            for (Acumulado a : existencia(limpia, piezasPorCaja)) {
                registros.add(a.respuesta(bloqueoAltaInicial(a, lotesHaero), loteDelAltaInicial(a, lotesHaero)));
            }
        } else {
            List<Acumulado> entradas = new ArrayList<>(compras(limpia, piezasPorCaja));
            entradas.addAll(entradasInv(limpia, piezasPorCaja));
            for (Acumulado a : entradas) {
                Long recibido = loteDelRegistro(a, lotesHaero);
                registros.add(a.respuesta(bloqueoEntrada(a, recibido, lotesHaero), recibido));
            }
        }
        // Lo que se puede recibir primero, y lo más reciente arriba.
        registros.sort(Comparator.comparing((RegistroIntelisisResponse r) -> r.bloqueo() != null)
                .thenComparing(RegistroIntelisisResponse::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return registros;
    }

    /** El registro que Almacén eligió, buscado otra vez en el servidor (no se confía en lo que manda la pantalla). */
    public Optional<RegistroIntelisisResponse> buscar(String origen, Integer idIntelisis, String clave, String lote) {
        String tipo = "EXISTENCIA".equals(origen) ? ALTA_INICIAL : ENTRADA;
        return listar(clave, tipo).stream()
                .filter(r -> r.origen().equals(origen))
                .filter(r -> idIntelisis == null ? r.idIntelisis() == null : idIntelisis.equals(r.idIntelisis()))
                .filter(r -> r.lote().equalsIgnoreCase(lote == null ? "" : lote.strip()))
                .findFirst();
    }

    // ---------- Lectura de la copia de Intelisis ----------

    private List<Acumulado> compras(String clave, Integer piezasPorCaja) {
        String sql = """
                SELECT r.compra_id, r.mov_id, r.fecha_emision, l.articulo, l.serie_lote, l.cantidad,
                       COALESCE(l.fecha_caducidad, x.caducidad) AS caducidad,
                       COALESCE(pr.nombre, r.proveedor) AS proveedor,
                       p.id AS pedido_id, p.numero_pedido, p.estatus AS pedido_estatus,
                       COALESCE(p.contrato, CAST(r.contrato_id AS VARCHAR(20))) AS contrato,
                       d.cantidad_esperada, d.cancelada_intelisis
                FROM intelisis_recepcion r
                JOIN intelisis_recepcion_lote l ON l.compra_id = r.compra_id
                LEFT JOIN intelisis_proveedor pr ON pr.proveedor = r.proveedor
                OUTER APPLY (SELECT MIN(e.fecha_caducidad) AS caducidad FROM intelisis_existencia_lote e
                             WHERE e.articulo = l.articulo AND e.serie_lote = l.serie_lote) x
                OUTER APPLY (SELECT TOP 1 pe.id, pe.numero_pedido, pe.estatus, pe.contrato
                             FROM intelisis_pedido ip JOIN pedido pe ON pe.compra_id_intelisis = ip.compra_id
                             WHERE r.origen LIKE 'Pedido%' AND ip.mov_id = r.origen_id
                             ORDER BY ip.fecha_emision DESC) p
                LEFT JOIN pedido_detalle d ON d.pedido_id = p.id AND d.medicamento_clave = ?
                WHERE l.articulo IN (?, ?) AND r.fecha_emision >= ? AND l.cantidad > 0
                """;
        Map<String, Acumulado> grupos = new LinkedHashMap<>();
        haero.query(sql, rs -> {
            int id = rs.getInt("compra_id");
            String lote = rs.getString("serie_lote");
            Acumulado a = grupos.computeIfAbsent(id + "|" + lote.toUpperCase(), k -> new Acumulado());
            if (a.origen == null) {
                a.origen = "COMPRA";
                a.idIntelisis = id;
                a.movimiento = MOV_RECEPCION + folio(rs.getString("mov_id"));
                a.fecha = fecha(rs, "fecha_emision");
                a.clave = clave;
                a.lote = lote;
                a.proveedor = rs.getString("proveedor");
                long pedidoId = rs.getLong("pedido_id");
                if (!rs.wasNull()) {
                    a.pedidoId = pedidoId;
                    a.numeroPedido = rs.getString("numero_pedido");
                    a.pedidoCancelado = "CANCELADO".equals(rs.getString("pedido_estatus"));
                    int esperada = rs.getInt("cantidad_esperada");
                    boolean hayPartida = !rs.wasNull();
                    int cancelada = rs.getInt("cancelada_intelisis");
                    a.partidaCancelada = hayPartida && esperada <= 0 && cancelada > 0;
                }
                a.contrato = rs.getString("contrato");
            }
            a.sumar(rs.getString("articulo"), rs.getBigDecimal("cantidad"), fecha(rs, "caducidad"), piezasPorCaja);
        }, clave, clave, clave + "-1", Date.valueOf(LocalDate.now().minusDays(diasEntradas)));
        return new ArrayList<>(grupos.values());
    }

    private List<Acumulado> entradasInv(String clave, Integer piezasPorCaja) {
        String sql = """
                SELECT e.inv_id, e.mov, e.mov_id, e.fecha_emision, e.referencia, e.concepto,
                       l.articulo, l.serie_lote, l.cantidad,
                       COALESCE(l.fecha_caducidad, x.caducidad) AS caducidad,
                       pr.nombre AS proveedor
                FROM intelisis_entrada_inv e
                JOIN intelisis_entrada_inv_lote l ON l.inv_id = e.inv_id
                LEFT JOIN intelisis_proveedor pr ON pr.proveedor = e.proveedor
                OUTER APPLY (SELECT MIN(ex.fecha_caducidad) AS caducidad FROM intelisis_existencia_lote ex
                             WHERE ex.articulo = l.articulo AND ex.serie_lote = l.serie_lote) x
                WHERE l.clave_base = ? AND e.fecha_emision >= ?
                """;
        Map<String, Acumulado> grupos = new LinkedHashMap<>();
        haero.query(sql, rs -> {
            int id = rs.getInt("inv_id");
            String lote = rs.getString("serie_lote");
            Acumulado a = grupos.computeIfAbsent(id + "|" + lote.toUpperCase(), k -> new Acumulado());
            if (a.origen == null) {
                String mov = rs.getString("mov");
                a.origen = "INV";
                a.idIntelisis = id;
                a.movimiento = mov + folio(rs.getString("mov_id"));
                a.referencia = unir(rs.getString("concepto"), rs.getString("referencia"));
                a.fecha = fecha(rs, "fecha_emision");
                a.clave = clave;
                a.lote = lote;
                // Las donaciones FONSABI no traen proveedor: queda el tipo de movimiento.
                String proveedor = rs.getString("proveedor");
                a.proveedor = proveedor != null ? proveedor : mov;
            }
            a.sumar(rs.getString("articulo"), rs.getBigDecimal("cantidad"), fecha(rs, "caducidad"), piezasPorCaja);
        }, clave, Date.valueOf(LocalDate.now().minusDays(diasEntradas)));
        return new ArrayList<>(grupos.values());
    }

    private List<Acumulado> existencia(String clave, Integer piezasPorCaja) {
        String sql = """
                SELECT s.articulo, s.serie_lote, s.existencia, s.fecha_caducidad, s.ultima_entrada, prov.nombre AS proveedor
                FROM intelisis_existencia_lote s
                -- El proveedor de la existencia: el de la última compra de ese lote, si la hay.
                OUTER APPLY (SELECT TOP 1 COALESCE(pr.nombre, r.proveedor) AS nombre
                             FROM intelisis_recepcion_lote rl
                             JOIN intelisis_recepcion r ON r.compra_id = rl.compra_id
                             LEFT JOIN intelisis_proveedor pr ON pr.proveedor = r.proveedor
                             WHERE rl.serie_lote = s.serie_lote AND rl.articulo IN (?, ?)
                             ORDER BY r.fecha_emision DESC) prov
                WHERE s.clave_base = ? AND s.almacen = ? AND s.existencia > 0
                """;
        Map<String, Acumulado> grupos = new LinkedHashMap<>();
        haero.query(sql, rs -> {
            String lote = rs.getString("serie_lote");
            Acumulado a = grupos.computeIfAbsent(lote.toUpperCase(), k -> new Acumulado());
            if (a.origen == null) {
                a.origen = "EXISTENCIA";
                a.movimiento = "Existencia " + almacenPrincipal;
                a.clave = clave;
                a.lote = lote;
                a.proveedor = rs.getString("proveedor");
            }
            LocalDate ultimaEntrada = fecha(rs, "ultima_entrada");
            if (ultimaEntrada != null && (a.fecha == null || ultimaEntrada.isAfter(a.fecha))) {
                a.fecha = ultimaEntrada;
            }
            a.sumar(rs.getString("articulo"), rs.getBigDecimal("existencia"), fecha(rs, "fecha_caducidad"), piezasPorCaja);
        }, clave, clave + "-1", clave, almacenPrincipal);
        return new ArrayList<>(grupos.values());
    }

    private Integer piezasPorCaja(String clave) {
        List<Integer> valores = haero.queryForList(
                "SELECT piezas_por_caja FROM medicamento WHERE clave = ?", Integer.class, clave);
        Integer valor = valores.isEmpty() ? null : valores.get(0);
        return valor != null && valor > 0 ? valor : null;
    }

    private List<LoteHaero> lotesHaero(String clave) {
        return haero.query("""
                SELECT id, numero_lote, origen_intelisis, id_intelisis, lote_intelisis, CAST(fecha_alta AS date) AS dia_alta
                FROM lote WHERE medicamento_clave = ?
                """, (rs, i) -> {
            int idIntelisis = rs.getInt("id_intelisis");
            return new LoteHaero(rs.getLong("id"), rs.getString("numero_lote"), rs.getString("origen_intelisis"),
                    rs.wasNull() ? null : idIntelisis, rs.getString("lote_intelisis"), fecha(rs, "dia_alta"));
        }, clave);
    }

    // ---------- Reglas de bloqueo ----------

    private static Long loteDelRegistro(Acumulado a, List<LoteHaero> lotes) {
        return lotes.stream()
                .filter(l -> a.origen.equals(l.origen()) && a.idIntelisis.equals(l.idIntelisis()) && a.lote.equalsIgnoreCase(l.loteIntelisis()))
                .map(LoteHaero::id).findFirst().orElse(null);
    }

    private static Long loteDelAltaInicial(Acumulado a, List<LoteHaero> lotes) {
        return lotes.stream()
                .filter(l -> a.lote.equalsIgnoreCase(l.loteIntelisis()) || a.lote.equalsIgnoreCase(l.numeroLote()))
                .map(LoteHaero::id).findFirst().orElse(null);
    }

    private static String bloqueoEntrada(Acumulado a, Long recibido, List<LoteHaero> lotes) {
        if (recibido != null) {
            return "Ya se recibió en HAERO.";
        }
        if (a.pedidoCancelado) {
            return "El pedido " + a.numeroPedido + " está cancelado en Intelisis: no recibir.";
        }
        if (a.partidaCancelada) {
            return "Intelisis canceló esta clave en el pedido " + a.numeroPedido + ": no recibir.";
        }
        if (a.caducidad == null) {
            return "Intelisis no tiene la caducidad de este lote; pide que la capturen antes de recibir.";
        }
        // La existencia que se dio de alta ya incluía lo que entró antes de ese día.
        boolean incluidoEnAlta = lotes.stream().anyMatch(l -> "EXISTENCIA".equals(l.origen())
                && a.lote.equalsIgnoreCase(l.loteIntelisis()) && l.diaAlta() != null && a.fecha != null
                && !l.diaAlta().isBefore(a.fecha));
        if (incluidoEnAlta) {
            return "Este lote ya entró a HAERO con el alta inicial, que ya incluía esta entrada.";
        }
        return null;
    }

    private static String bloqueoAltaInicial(Acumulado a, List<LoteHaero> lotes) {
        if (loteDelAltaInicial(a, lotes) != null) {
            return "Ese lote ya está en HAERO.";
        }
        if (a.caducidad == null) {
            return "Intelisis no tiene la caducidad de este lote; pide que la capturen antes de darlo de alta.";
        }
        return null;
    }

    // ---------- Utilidades ----------

    private static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }

    private static String folio(String movId) {
        return movId == null || movId.isBlank() ? "" : " " + movId.strip();
    }

    private static String unir(String a, String b) {
        String texto = ((a == null ? "" : a.strip()) + " · " + (b == null ? "" : b.strip())).replaceAll("^ · | · $", "").strip();
        return texto.isEmpty() || texto.equals("·") ? null : texto;
    }

    private record LoteHaero(Long id, String numeroLote, String origen, Integer idIntelisis, String loteIntelisis, LocalDate diaAlta) {
    }

    /** Un registro mientras se suman sus renglones (la clave base y la "-1" del mismo lote). */
    private static final class Acumulado {
        String origen;
        Integer idIntelisis;
        String movimiento;
        String referencia;
        LocalDate fecha;
        String clave;
        String lote;
        LocalDate caducidad;
        BigDecimal piezas = BigDecimal.ZERO;
        BigDecimal cajas = BigDecimal.ZERO;
        boolean piezasDesconocidas;
        boolean cajasDesconocidas;
        String proveedor;
        Long pedidoId;
        String numeroPedido;
        String contrato;
        boolean pedidoCancelado;
        boolean partidaCancelada;

        void sumar(String articulo, BigDecimal cantidad, LocalDate caducidadRenglon, Integer piezasPorCaja) {
            BigDecimal valor = cantidad == null ? BigDecimal.ZERO : cantidad;
            boolean enPiezas = articulo != null && articulo.strip().matches(".*-\\d+$");
            if (enPiezas) {
                piezas = piezas.add(valor);
                if (piezasPorCaja == null) {
                    cajasDesconocidas = true;
                } else {
                    cajas = cajas.add(valor.divide(BigDecimal.valueOf(piezasPorCaja), 2, RoundingMode.HALF_UP));
                }
            } else {
                cajas = cajas.add(valor);
                if (piezasPorCaja == null) {
                    piezasDesconocidas = true;
                } else {
                    piezas = piezas.add(valor.multiply(BigDecimal.valueOf(piezasPorCaja)));
                }
            }
            if (caducidadRenglon != null && (caducidad == null || caducidadRenglon.isBefore(caducidad))) {
                caducidad = caducidadRenglon;
            }
        }

        RegistroIntelisisResponse respuesta(String bloqueo, Long loteHaeroId) {
            return new RegistroIntelisisResponse(origen, idIntelisis, movimiento, referencia, fecha, clave, lote, caducidad,
                    piezasDesconocidas ? null : piezas.stripTrailingZeros(),
                    cajasDesconocidas ? null : cajas.stripTrailingZeros(),
                    proveedor, pedidoId, numeroPedido, contrato, bloqueo, loteHaeroId);
        }
    }
}
