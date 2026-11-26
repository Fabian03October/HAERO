package com.example.backend.farmacia.service;

import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistroIntelisisResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.farmacia.dto.AltaInicialRequest;
import com.example.backend.farmacia.dto.PartidaAltaInicial;
import com.example.backend.almacen.service.ServicioRecepcion;
import com.example.backend.almacen.service.ServicioRegistrosIntelisis;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.farmacia.entity.ConteoInicial;
import com.example.backend.farmacia.entity.ConteoInicialRenglon;
import com.example.backend.farmacia.repository.ConteoInicialRenglonRepository;
import com.example.backend.farmacia.repository.ConteoInicialRepository;
import com.example.backend.farmacia.repository.LoteFarmaciaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // Solo para leer la copia de Intelisis (intelisis_existencia_lote), que no tiene entidad.
    private final JdbcTemplate haero;
    private final ConteoInicialRepository cierres;
    private final ConteoInicialRenglonRepository renglones;
    private final LoteFarmaciaRepository lotesFarmacia;
    private final LoteRepository lotesAlmacen;
    private final EntityManager em;
    private final ServicioRecepcion recepcion;
    private final ServicioRegistrosIntelisis registrosIntelisis;
    private final ServicioInventarioFarmacia farmacia;

    public ServicioConteoInicial(JdbcTemplate haero, ConteoInicialRepository cierres, ConteoInicialRenglonRepository renglones,
                                 LoteFarmaciaRepository lotesFarmacia, LoteRepository lotesAlmacen, EntityManager em,
                                 ServicioRecepcion recepcion, ServicioRegistrosIntelisis registrosIntelisis,
                                 ServicioInventarioFarmacia farmacia) {
        this.haero = haero;
        this.cierres = cierres;
        this.renglones = renglones;
        this.lotesFarmacia = lotesFarmacia;
        this.lotesAlmacen = lotesAlmacen;
        this.em = em;
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

    /** Lote que Intelisis tiene en existencia, para elegirlo en vez de escribir lote y caducidad. */
    public record LoteIntelisis(String lote, LocalDate caducidad, java.math.BigDecimal piezas) {
    }

    /**
     * Lotes con existencia y caducidad de una clave en el almacén de medicamentos de Intelisis (la copia
     * que llena la sincronización). Almacén y Farmacia son el mismo inventario en Intelisis, así que
     * salen los de los dos, de todos los almacenes que se sincronizan (25301 y 25301-C), sin restricción. Piezas: la clave "-1" ya viene en piezas; la base, en cajas.
     * Los que no traen caducidad en Intelisis también salen (caducidad null): se escribe a mano al elegirlos.
     */
    public List<LoteIntelisis> lotesIntelisis(String clave) {
        String limpia = ServicioInventarioFarmacia.texto(clave);
        if (limpia == null) {
            throw malo("Falta la clave del medicamento");
        }
        return haero.query("""
                SELECT e.serie_lote, MIN(e.fecha_caducidad) AS caducidad,
                       SUM(CASE WHEN e.articulo LIKE '%-1' THEN e.existencia ELSE e.existencia * COALESCE(m.piezas_por_caja, 1) END) AS piezas
                FROM intelisis_existencia_lote e
                LEFT JOIN medicamento m ON m.clave = e.clave_base
                WHERE e.clave_base = ? AND e.existencia > 0
                GROUP BY e.serie_lote
                ORDER BY CASE WHEN MIN(e.fecha_caducidad) IS NULL THEN 1 ELSE 0 END, MIN(e.fecha_caducidad), e.serie_lote
                """, (rs, n) -> new LoteIntelisis(rs.getString(1).trim(), rs.getDate(2) == null ? null : rs.getDate(2).toLocalDate(), rs.getBigDecimal(3)),
                limpia);
    }

    @Transactional(readOnly = true)
    public Estado estado(String area) {
        ConteoInicial cierre = cierres.findByArea(area).orElse(null);
        List<Renglon> lista = renglones.findByAreaOrderByUbicacionAscMedicamentoClaveAscLoteAsc(area).stream()
                .map(ServicioConteoInicial::renglon).toList();
        int sinCantidad = (int) lista.stream().filter(r -> r.cantidad() == null).count();
        return new Estado(area, cierre != null, cierre == null ? null : cierre.getCerradoEn(),
                cierre == null ? null : cierre.getCerradoPor().getNombreCompleto(), lista.size(), sinCantidad, lista);
    }

    /** Ya no se puede contar ni dar de alta inicial cuando el área guardó su inventario inicial. */
    public void exigirAbierto(String area) {
        cierres.findById(area).ifPresent(c -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inventario inicial de " + nombre(area) + " ya se guardó el "
                    + c.getCerradoEn().toLocalDate() + "; no se puede volver a hacer");
        });
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
        if (ubicacion == null) {
            throw malo("Indica el anaquel o la ubicación de la caja");
        }
        if (ubicacion.length() > 40) {
            throw malo("La ubicación puede tener hasta 40 caracteres");
        }
        LoteYCaducidad lc = loteYCaducidad(area, clave, lote, nuevo.caducidad());
        lote = lc.lote();
        LocalDate caducidad = lc.caducidad();
        List<LocalDate> otras = renglones.caducidadesDelLote(area, clave, lote);
        if (otras.stream().anyMatch(d -> !d.equals(caducidad))) {
            throw malo("El lote " + lote + " ya está en el conteo con caducidad " + otras.get(0) + "; revisa la caducidad de la caja");
        }
        if (lote.length() > 50) {
            throw malo("El lote puede tener hasta 50 caracteres");
        }

        // Volver a escanear el mismo lote en la misma ubicación no lo duplica.
        Long existente = idDe(area, clave, lote, ubicacion);
        if (existente != null) {
            return renglon(existente);
        }
        ConteoInicialRenglon renglon = new ConteoInicialRenglon();
        renglon.setArea(area);
        renglon.setCodigo(codigo);
        renglon.setMedicamento(medicamento);
        renglon.setLote(lote);
        renglon.setCaducidad(caducidad);
        renglon.setUbicacion(ubicacion);
        renglon.setRegistradoPor(farmacia.usuario(nombreUsuario));
        renglon.setRegistradoEn(LocalDateTime.now());
        try {
            renglones.saveAndFlush(renglon);
        } catch (DataIntegrityViolationException e) {
            // Otra persona lo escaneó en el mismo instante (llave única de área, clave, lote y ubicación).
            // Con JPA la transacción ya no sirve después del choque: se pide volver a escanear, y ahí sale el suyo.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otra persona acaba de registrar este lote en ese estante; vuelve a escanear");
        }
        return renglon(renglon);
    }

    @Transactional
    public Renglon capturarCantidad(String area, long id, Integer cantidad, String nombreUsuario) {
        exigirAbierto(area);
        if (cantidad == null || cantidad < 0) {
            throw malo("Escribe lo que hay en el anaquel (0 si no había)");
        }
        ConteoInicialRenglon renglon = renglones.findByIdAndArea(id, area)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo"));
        renglon.setCantidad(cantidad);
        renglon.setContadoPor(farmacia.usuario(nombreUsuario));
        renglon.setContadoEn(LocalDateTime.now());
        return renglon(renglon);
    }

    /**
     * Pasa un renglón a otro estante (se registró en el que no era). Si en ese estante ya estaba el
     * mismo lote, se juntan en uno y se suman las piezas contadas.
     */
    @Transactional
    public Renglon mover(String area, long id, String ubicacionNueva, String nombreUsuario) {
        exigirAbierto(area);
        String destino = ServicioInventarioFarmacia.texto(ubicacionNueva);
        if (destino == null) {
            throw malo("Indica el anaquel o la ubicación");
        }
        if (destino != null && destino.length() > 40) {
            throw malo("La ubicación puede tener hasta 40 caracteres");
        }
        ConteoInicialRenglon origen = renglones.findByIdAndArea(id, area)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo"));
        Long otro = idDe(area, origen.getMedicamento().getClave(), origen.getLote(), destino);
        if (otro == null) {
            origen.setUbicacion(destino);
            return renglon(origen);
        }
        if (otro == id) {
            return renglon(origen);
        }
        ConteoInicialRenglon ya = renglones.findById(otro).orElseThrow();
        if (origen.getCantidad() != null || ya.getCantidad() != null) {
            ya.setCantidad((origen.getCantidad() == null ? 0 : origen.getCantidad()) + (ya.getCantidad() == null ? 0 : ya.getCantidad()));
            ya.setContadoPor(farmacia.usuario(nombreUsuario));
            ya.setContadoEn(LocalDateTime.now());
        }
        renglones.delete(origen);
        // Se escribe ya: si luego se mueve otro renglón al mismo estante (renombrar), la búsqueda debe verlo.
        renglones.flush();
        return renglon(ya);
    }

    /**
     * Cambia el nombre de un estante: todos sus renglones pasan al nombre nuevo. Si ya existe un estante
     * con ese nombre y tiene alguno de los mismos lotes, se juntan y se suman las piezas (igual que mover).
     */
    @Transactional
    public Estado renombrarEstante(String area, String actual, String nuevo, String nombreUsuario) {
        exigirAbierto(area);
        String de = ServicioInventarioFarmacia.texto(actual);
        List<Long> ids = renglones.idsDelEstante(area, de);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese estante no tiene renglones en el conteo");
        }
        for (Long id : ids) {
            mover(area, id, nuevo, nombreUsuario);
        }
        return estado(area);
    }

    /** Corrección de un renglón antes de guardar el inventario: clave, lote o caducidad mal capturados. */
    public record Correccion(String clave, String lote, LocalDate caducidad) {
    }

    /**
     * Corrige clave, lote o caducidad de un renglón (verificación antes de guardar). Mismas reglas que al
     * registrarlo: en Almacén el lote debe existir en Intelisis (de ahí sale la caducidad); en Farmacia la
     * caducidad debe coincidir con la del mismo lote en otros estantes. No puede quedar repetido en su estante.
     */
    @Transactional
    public Renglon corregir(String area, long id, Correccion correccion, String nombreUsuario) {
        exigirAbierto(area);
        ConteoInicialRenglon renglon = renglones.findByIdAndArea(id, area)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo"));
        String claveNueva = ServicioInventarioFarmacia.texto(correccion.clave());
        Medicamento medicamento = claveNueva == null ? renglon.getMedicamento() : farmacia.medicamento(claveNueva);
        String clave = medicamento.getClave();
        String lote = ServicioInventarioFarmacia.texto(correccion.lote());
        if (lote == null) {
            throw malo("Falta el lote de la caja");
        }
        if (lote.length() > 50) {
            throw malo("El lote puede tener hasta 50 caracteres");
        }
        LoteYCaducidad lc = loteYCaducidad(area, clave, lote, correccion.caducidad());
        lote = lc.lote();
        LocalDate caducidad = lc.caducidad();
        String elLote = lote;
        boolean otraCaducidad = renglones.findByAreaOrderByUbicacionAscMedicamentoClaveAscLoteAsc(area).stream()
                .anyMatch(r -> r.getId() != id && r.getMedicamento().getClave().equals(clave) && r.getLote().equalsIgnoreCase(elLote)
                        && !r.getCaducidad().equals(caducidad));
        if (otraCaducidad) {
            throw malo("El lote " + lote + " ya está en el conteo con otra caducidad; corrige los dos renglones a la misma");
        }
        Long otro = idDe(area, clave, lote, renglon.getUbicacion());
        if (otro != null && otro != id) {
            throw malo("Ese lote ya está en el mismo estante en otro renglón; borra uno de los dos o cámbialo de estante");
        }
        renglon.setMedicamento(medicamento);
        renglon.setLote(lote);
        renglon.setCaducidad(caducidad);
        renglones.flush();
        return renglon(renglon);
    }

    record LoteYCaducidad(String lote, LocalDate caducidad) {
    }

    /**
     * Igual en Almacén y Farmacia: si el lote está en Intelisis, su caducidad (y su nombre, con la terminación que
     * tenga allá) salen de Intelisis. Si no está (pasa: lotes que están en el anaquel y no en el sistema), se
     * acepta con la caducidad escrita de la caja; quién lo registró queda en el renglón.
     */
    LoteYCaducidad loteYCaducidad(String area, String clave, String lote, LocalDate caducidadEscrita) {
        if (ALMACEN.equals(area)) {
            RegistroIntelisisResponse registro = registrosIntelisis.buscar("EXISTENCIA", null, clave, lote).orElse(null);
            if (registro != null && registro.bloqueo() != null) {
                throw malo(registro.bloqueo());
            }
            if (registro != null && registro.caducidad() != null) {
                return new LoteYCaducidad(registro.lote(), registro.caducidad());
            }
        } else {
            String base = ServicioTrazabilidad.loteBase(lote);
            LoteIntelisis enIntelisis = lotesIntelisis(clave).stream()
                    .filter(l -> l.caducidad() != null && (l.lote().equalsIgnoreCase(lote) || ServicioTrazabilidad.loteBase(l.lote()).equalsIgnoreCase(base)))
                    .findFirst().orElse(null);
            if (enIntelisis != null) {
                return new LoteYCaducidad(lote, enIntelisis.caducidad());
            }
        }
        if (caducidadEscrita == null) {
            throw malo("Intelisis no tiene el lote " + lote + " de " + clave + ": escribe la caducidad que trae la caja");
        }
        return new LoteYCaducidad(lote, caducidadEscrita);
    }

    @Transactional
    public void quitar(String area, long id) {
        exigirAbierto(area);
        renglones.delete(renglones.findByIdAndArea(id, area)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese renglón no está en el conteo")));
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
                    // Si Intelisis no tiene el lote, se da de alta con la caducidad que se escribió en el conteo.
                    long loteId = recepcion.registrarEntrada(alta, nombreUsuario, primero.caducidad()).getLoteId();
                    // Farmacia hace su inventario inicial primero: sus lotes quedaron sin lote de Almacén;
                    // se ligan ahora para la trazabilidad (HU33).
                    lotesFarmacia.findByMedicamentoClaveAndNumeroLoteIgnoreCase(primero.clave(), primero.lote())
                            .filter(l -> l.getLoteAlmacen() == null)
                            .ifPresent(l -> l.setLoteAlmacen(lotesAlmacen.getReferenceById(loteId)));
                } catch (ResponseStatusException e) {
                    throw new ResponseStatusException(e.getStatusCode(), "Lote " + primero.lote() + " de " + primero.clave() + ": " + e.getReason());
                }
            }
        } else if (!contados.isEmpty()) {
            // Farmacia tiene un estante por lote: si el mismo lote quedó en dos estantes, se guardan los dos ("A1 / B2").
            Map<String, String> estantes = new LinkedHashMap<>();
            contados.forEach(r -> estantes.merge(r.clave() + "|" + r.lote().toUpperCase(Locale.ROOT), r.ubicacion(),
                    (a, b) -> a.contains(b) ? a : a + " / " + b));
            AltaInicialRequest alta = new AltaInicialRequest();
            alta.setPartidas(contados.stream().map(r -> {
                PartidaAltaInicial p = new PartidaAltaInicial();
                p.setClave(r.clave());
                p.setLote(r.lote());
                p.setCaducidad(r.caducidad());
                p.setPiezas(r.cantidad());
                p.setCodigo(r.codigo());
                String estante = estantes.get(r.clave() + "|" + r.lote().toUpperCase(Locale.ROOT));
                p.setUbicacion(estante == null || estante.length() <= 40 ? estante : estante.substring(0, 40));
                return p;
            }).toList());
            farmacia.altaInicial(alta, nombreUsuario);
        }
        // Al final: si dos personas guardan a la vez, la segunda choca con la llave del área y no se duplica nada.
        ConteoInicial cierre = new ConteoInicial();
        cierre.setArea(area);
        cierre.setCerradoEn(LocalDateTime.now());
        cierre.setCerradoPor(farmacia.usuario(nombreUsuario));
        try {
            // persist (no merge): debe ser un INSERT nuevo y chocar si otra persona ya guardó.
            em.persist(cierre);
            em.flush();
        } catch (PersistenceException | DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otra persona acaba de guardar el inventario inicial de " + nombre(area));
        }
        return estado(area);
    }

    // El código ya aprendido (HU42) o, si es nuevo, el de otro renglón del conteo.
    /** Clave de un código: del catálogo o del conteo de cualquiera de las dos áreas (lo escaneado en una sirve en la otra). */
    public Map<String, String> clavePorCodigo(String codigo) {
        String clave = claveDelCodigo(ServicioInventarioFarmacia.texto(codigo));
        if (clave == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Código nuevo: elige de qué medicamento es la caja");
        }
        return Map.of("clave", clave, "nombre", farmacia.medicamento(clave).getNombreGenerico());
    }

    private String claveDelCodigo(String codigo) {
        return farmacia.buscarCodigo(codigo).map(c -> c.getMedicamento().getClave())
                .orElseGet(() -> renglones.clavesDelCodigo(codigo).stream().findFirst().orElse(null));
    }

    private Long idDe(String area, String clave, String lote, String ubicacion) {
        return renglones.idsDelLoteEn(area, clave, lote, ubicacion).stream().findFirst().orElse(null);
    }

    private Renglon renglon(long id) {
        return renglon(renglones.findById(id).orElseThrow());
    }

    private static Renglon renglon(ConteoInicialRenglon r) {
        return new Renglon(r.getId(), r.getCodigo(), r.getMedicamento().getClave(), r.getMedicamento().getNombreGenerico(),
                r.getMedicamento().getPiezasPorCaja(), r.getLote(), r.getCaducidad(), r.getUbicacion(), r.getCantidad(),
                r.getRegistradoPor().getNombreCompleto(), r.getRegistradoEn(),
                r.getContadoPor() == null ? null : r.getContadoPor().getNombreCompleto(), r.getContadoEn());
    }

    private static String nombre(String area) {
        return ALMACEN.equals(area) ? "Almacén" : "Farmacia";
    }

    private static ResponseStatusException malo(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
