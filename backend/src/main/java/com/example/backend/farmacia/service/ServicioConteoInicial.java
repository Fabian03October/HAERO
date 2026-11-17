package com.example.backend.farmacia.service;

import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistroIntelisisResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.farmacia.dto.AltaInicialRequest;
import com.example.backend.farmacia.dto.PartidaAltaInicial;
import com.example.backend.almacen.service.ServicioRecepcion;
import com.example.backend.almacen.service.ServicioRegistrosIntelisis;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conteo del inventario inicial de Almacén (HU21) y de Farmacia (HU25), en dos tiempos:
 *  1) se registran los códigos y lotes de cada anaquel (sin cantidades),
 *  2) el personal capacitado captura lo que hay de cada renglón,
 *  3) al guardar se da de alta todo de una vez con el alta de siempre y el área queda cerrada:
 *     ya no se puede contar ni dar de alta inicial otra vez (la pantalla se oculta del menú).
 * Todo vive en el servidor: varias personas trabajan a la vez y nada se pierde si se cierra la sesión.
 */
@Service
public class ServicioConteoInicial {

    public static final String ALMACEN = "ALMACEN";
    public static final String FARMACIA = "FARMACIA";

    private final JdbcTemplate haero;
    private final ServicioRecepcion recepcion;
    private final ServicioRegistrosIntelisis registrosIntelisis;
    private final ServicioInventarioFarmacia farmacia;

    public ServicioConteoInicial(JdbcTemplate haero, ServicioRecepcion recepcion, ServicioRegistrosIntelisis registrosIntelisis,
                                 ServicioInventarioFarmacia farmacia) {
        this.haero = haero;
        this.recepcion = recepcion;
        this.registrosIntelisis = registrosIntelisis;
        this.farmacia = farmacia;
    }

    /** Cantidad: cajas en Almacén, piezas en Farmacia; null mientras no la capturan. */
    public record Renglon(long id, String codigo, String clave, String medicamento, Integer piezasPorCaja, String lote,
                          LocalDate caducidad, String ubicacion, Integer cantidad, String registradoPor, LocalDateTime registradoEn,
                          String contadoPor, LocalDateTime contadoEn) {
    }

    public record Estado(String area, boolean cerrado, LocalDateTime cerradoEn, String cerradoPor, int totalRenglones,
                         int sinCantidad, List<Renglon> renglones) {
    }

    /** Lo que se escanea: el código, el lote y el anaquel. La clave solo si el código es nuevo. */
    public record NuevoRenglon(String codigo, String clave, String lote, LocalDate caducidad, String ubicacion) {
    }

    private static final String SELECT_RENGLON = """
            SELECT r.id, r.codigo, r.medicamento_clave, m.nombre_generico, m.piezas_por_caja, r.lote, r.caducidad, r.ubicacion,
                   r.cantidad, ur.nombre_completo AS registro, r.registrado_en, uc.nombre_completo AS conteo, r.contado_en
            FROM conteo_inicial_renglon r
            JOIN medicamento m ON m.clave = r.medicamento_clave
            JOIN usuario ur ON ur.id = r.registrado_por
            LEFT JOIN usuario uc ON uc.id = r.contado_por
            """;

    public Estado estado(String area) {
        List<Map<String, Object>> cierre = haero.queryForList("""
                SELECT c.cerrado_en, u.nombre_completo FROM conteo_inicial c JOIN usuario u ON u.id = c.cerrado_por WHERE c.area = ?
                """, area);
        List<Renglon> renglones = haero.query(SELECT_RENGLON + " WHERE r.area = ? ORDER BY r.ubicacion, r.medicamento_clave, r.lote",
                ServicioConteoInicial::renglon, area);
        int sinCantidad = (int) renglones.stream().filter(r -> r.cantidad() == null).count();
        Map<String, Object> c = cierre.isEmpty() ? Map.of() : cierre.get(0);
        return new Estado(area, !cierre.isEmpty(), c.get("cerrado_en") instanceof Timestamp t ? t.toLocalDateTime() : null,
                (String) c.get("nombre_completo"), renglones.size(), sinCantidad, renglones);
    }

    /** Ya no se puede contar ni dar de alta inicial cuando el área guardó su inventario inicial. */
    public void exigirAbierto(String area) {
        List<Timestamp> cerrado = haero.queryForList("SELECT cerrado_en FROM conteo_inicial WHERE area = ?", Timestamp.class, area);
        if (!cerrado.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inventario inicial de " + nombre(area) + " ya se guardó el "
                    + cerrado.get(0).toLocalDateTime().toLocalDate() + "; no se puede volver a hacer");
        }
    }

    @Transactional
    public Renglon agregar(String area, NuevoRenglon nuevo, String nombreUsuario) {
        exigirAbierto(area);
        String codigo = ServicioInventarioFarmacia.texto(nuevo.codigo());
        if (codigo == null) {
            throw malo("Escanea el código de barras de la caja");
        }
        if (codigo.length() > 60) {
            throw malo("El código escaneado es demasiado largo; escanea el código de barras de la caja");
        }
        String conocida = claveDelCodigo(codigo);
        String pedida = ServicioInventarioFarmacia.texto(nuevo.clave());
        if (conocida == null && pedida == null) {
            // La pantalla pide la clave y vuelve a mandar el renglón.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Código nuevo (" + codigo + "): elige la clave de esta caja");
        }
        if (conocida != null && pedida != null && !conocida.equals(pedida)) {
            throw malo("El código " + codigo + " ya es de la clave " + conocida + "; revisa la caja");
        }
        Medicamento medicamento = farmacia.medicamento(conocida != null ? conocida : pedida);
        String clave = medicamento.getClave();
        String lote = ServicioInventarioFarmacia.texto(nuevo.lote());
        if (lote == null) {
            throw malo("Falta el lote de la caja");
        }
        String ubicacion = ServicioInventarioFarmacia.texto(nuevo.ubicacion());
        if (ubicacion != null && ubicacion.length() > 40) {
            throw malo("La ubicación puede tener hasta 40 caracteres");
        }
        LocalDate caducidad;
        if (ALMACEN.equals(area)) {
            if (ubicacion == null) {
                throw malo("Indica el anaquel o la ubicación de la caja");
            }
            // Igual que el alta de siempre: el lote tiene que estar en la existencia de Intelisis.
            String buscado = lote;
            RegistroIntelisisResponse registro = registrosIntelisis.buscar("EXISTENCIA", null, clave, lote)
                    .orElseThrow(() -> malo("Intelisis no tiene existencia del lote " + buscado + " de " + clave + " en el almacén de medicamentos"));
            if (registro.bloqueo() != null) {
                throw malo(registro.bloqueo());
            }
            lote = registro.lote();
            caducidad = registro.caducidad();
        } else {
            if (nuevo.caducidad() == null) {
                throw malo("Falta la caducidad de la caja");
            }
            caducidad = nuevo.caducidad();
            List<java.sql.Date> otras = haero.queryForList("""
                    SELECT DISTINCT caducidad FROM conteo_inicial_renglon WHERE area = ? AND medicamento_clave = ? AND lote = ?
                    """, java.sql.Date.class, area, clave, lote);
            if (otras.stream().anyMatch(d -> !d.toLocalDate().equals(caducidad))) {
                throw malo("El lote " + lote + " ya está en el conteo con caducidad " + otras.get(0) + "; revisa la caducidad de la caja");
            }
        }
        if (lote.length() > 50) {
            throw malo("El lote puede tener hasta 50 caracteres");
        }

        // Volver a escanear el mismo lote en la misma ubicación no lo duplica.
        Long existente = idDe(area, clave, lote, ubicacion);
        if (existente != null) {
            return renglon(existente);
        }
        long usuario = farmacia.usuario(nombreUsuario).getId();
        try {
            Long id = haero.queryForObject("""
                    INSERT INTO conteo_inicial_renglon (area, codigo, medicamento_clave, lote, caducidad, ubicacion, registrado_por, registrado_en)
                    OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Long.class, area, codigo, clave, lote, java.sql.Date.valueOf(caducidad), ubicacion, usuario,
                    Timestamp.valueOf(LocalDateTime.now()));
            return renglon(id);
        } catch (DuplicateKeyException e) {
            // Otra persona lo escaneó al mismo tiempo.
            return renglon(idDe(area, clave, lote, ubicacion));
        }
    }

    @Transactional
    public Renglon capturarCantidad(String area, long id, Integer cantidad, String nombreUsuario) {
        exigirAbierto(area);
        if (cantidad == null || cantidad < 0) {
            throw malo("Escribe lo que hay en el anaquel (0 si no había)");
        }
        long usuario = farmacia.usuario(nombreUsuario).getId();
        int cambiados = haero.update("UPDATE conteo_inicial_renglon SET cantidad = ?, contado_por = ?, contado_en = ? WHERE id = ? AND area = ?",
                cantidad, usuario, Timestamp.valueOf(LocalDateTime.now()), id, area);
        if (cambiados == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo");
        }
        return renglon(id);
    }

    @Transactional
    public void quitar(String area, long id) {
        exigirAbierto(area);
        if (haero.update("DELETE FROM conteo_inicial_renglon WHERE id = ? AND area = ?", id, area) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo");
        }
    }

    /**
     * Guarda el inventario inicial: todo o nada. Usa el alta de siempre (HU21 / HU25), así que se
     * validan Intelisis, los códigos y los lotes igual que antes. Los renglones con 0 no se dan de alta.
     */
    @Transactional
    public Estado cerrar(String area, String nombreUsuario) {
        exigirAbierto(area);
        List<Renglon> renglones = estado(area).renglones();
        if (renglones.isEmpty()) {
            throw malo("El conteo no tiene renglones");
        }
        long sinCantidad = renglones.stream().filter(r -> r.cantidad() == null).count();
        if (sinCantidad > 0) {
            throw malo("Faltan " + sinCantidad + " renglones sin cantidad; captúralos (0 si no había) antes de guardar");
        }
        long usuario = farmacia.usuario(nombreUsuario).getId();
        List<Renglon> contados = renglones.stream().filter(r -> r.cantidad() > 0).toList();
        if (ALMACEN.equals(area)) {
            // Un lote que está en varios anaqueles es una sola alta con varias ubicaciones.
            Map<String, List<Renglon>> porLote = new LinkedHashMap<>();
            contados.forEach(r -> porLote.computeIfAbsent(r.clave() + "|" + r.lote().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(r));
            for (List<Renglon> grupo : porLote.values()) {
                Renglon primero = grupo.get(0);
                RegistrarEntradaRequest alta = new RegistrarEntradaRequest();
                // shortcut: si un mismo lote se escaneó con dos códigos distintos solo se aprende el primero; casi no pasa.
                alta.setCodigo(primero.codigo());
                alta.setClave(primero.clave());
                alta.setOrigen("EXISTENCIA");
                alta.setLote(primero.lote());
                alta.setObservaciones("Conteo inicial");
                alta.setUbicaciones(grupo.stream().map(r -> {
                    UbicacionCantidad u = new UbicacionCantidad();
                    u.setUbicacion(r.ubicacion());
                    u.setCajas(r.cantidad());
                    return u;
                }).toList());
                try {
                    long loteId = recepcion.registrarEntrada(alta, nombreUsuario).getLoteId();
                    // Farmacia hace su inventario inicial primero: sus lotes quedaron sin lote de Almacén;
                    // se ligan ahora para la trazabilidad (HU33).
                    haero.update("UPDATE farmacia_lote SET lote_id = ? WHERE medicamento_clave = ? AND numero_lote = ? AND lote_id IS NULL",
                            loteId, primero.clave(), primero.lote());
                } catch (ResponseStatusException e) {
                    throw new ResponseStatusException(e.getStatusCode(), "Lote " + primero.lote() + " de " + primero.clave() + ": " + e.getReason());
                }
            }
        } else if (!contados.isEmpty()) {
            AltaInicialRequest alta = new AltaInicialRequest();
            alta.setPartidas(contados.stream().map(r -> {
                PartidaAltaInicial p = new PartidaAltaInicial();
                p.setClave(r.clave());
                p.setLote(r.lote());
                p.setCaducidad(r.caducidad());
                p.setPiezas(r.cantidad());
                p.setCodigo(r.codigo());
                return p;
            }).toList());
            farmacia.altaInicial(alta, nombreUsuario);
        }
        // Al final: si dos personas guardan a la vez, la segunda choca con la llave del área y no se duplica nada.
        try {
            haero.update("INSERT INTO conteo_inicial (area, cerrado_en, cerrado_por) VALUES (?, ?, ?)",
                    area, Timestamp.valueOf(LocalDateTime.now()), usuario);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otra persona acaba de guardar el inventario inicial de " + nombre(area));
        }
        return estado(area);
    }

    // El código ya aprendido (HU42) o, si es nuevo, el de otro renglón del conteo.
    private String claveDelCodigo(String codigo) {
        return farmacia.buscarCodigo(codigo).map(c -> c.getMedicamento().getClave())
                .orElseGet(() -> haero.queryForList("SELECT TOP 1 medicamento_clave FROM conteo_inicial_renglon WHERE codigo = ?",
                        String.class, codigo).stream().findFirst().orElse(null));
    }

    private Long idDe(String area, String clave, String lote, String ubicacion) {
        return haero.queryForList("""
                SELECT id FROM conteo_inicial_renglon
                WHERE area = ? AND medicamento_clave = ? AND lote = ? AND (ubicacion = ? OR (ubicacion IS NULL AND ? IS NULL))
                """, Long.class, area, clave, lote, ubicacion, ubicacion).stream().findFirst().orElse(null);
    }

    private Renglon renglon(long id) {
        return haero.query(SELECT_RENGLON + " WHERE r.id = ?", ServicioConteoInicial::renglon, id).get(0);
    }

    private static Renglon renglon(ResultSet rs, int n) throws SQLException {
        Timestamp registrado = rs.getTimestamp("registrado_en");
        Timestamp contado = rs.getTimestamp("contado_en");
        return new Renglon(rs.getLong("id"), rs.getString("codigo"), rs.getString("medicamento_clave"), rs.getString("nombre_generico"),
                rs.getObject("piezas_por_caja", Integer.class), rs.getString("lote"), rs.getDate("caducidad").toLocalDate(),
                rs.getString("ubicacion"), rs.getObject("cantidad", Integer.class), rs.getString("registro"),
                registrado == null ? null : registrado.toLocalDateTime(), rs.getString("conteo"),
                contado == null ? null : contado.toLocalDateTime());
    }

    private static String nombre(String area) {
        return ALMACEN.equals(area) ? "Almacén" : "Farmacia";
    }

    private static ResponseStatusException malo(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
