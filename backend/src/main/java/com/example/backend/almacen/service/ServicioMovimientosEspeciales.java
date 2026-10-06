package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CaducadoResponse;
import com.example.backend.almacen.dto.CanjeRequest;
import com.example.backend.almacen.dto.CanjeResponse;
import com.example.backend.almacen.dto.MovimientoExternoRequest;
import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.ExpedienteExterno;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Cubre HU18 (canje de lote), HU19 (apartado de caducados) y HU20 (prestamos y
 * transferencias), tal como lo agrupa la carta CRC de ServicioMovimientosEspeciales
 * en el documento de diseno. Cada operacion deja sus movimientos con el usuario
 * que la registro; ninguno es de tipo SALIDA, asi que el CPM no los cuenta (HU16 CA4).
 */
@Service
public class ServicioMovimientosEspeciales {

    /** Un lote se puede canjear cuando le quedan menos de estos meses de vida (HU18). */
    public static final int MESES_PARA_CANJE = 9;

    private final MedicamentoRepository medicamentoRepository;
    private final LoteRepository loteRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioMovimientos servicioMovimientos;

    public ServicioMovimientosEspeciales(MedicamentoRepository medicamentoRepository,
                                         LoteRepository loteRepository,
                                         ExistenciaRepository existenciaRepository,
                                         MovimientoRepository movimientoRepository,
                                         UsuarioRepository usuarioRepository,
                                         ServicioMovimientos servicioMovimientos) {
        this.medicamentoRepository = medicamentoRepository;
        this.loteRepository = loteRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.servicioMovimientos = servicioMovimientos;
    }

    // ---------- HU18: canje de lote ----------

    @Transactional
    public CanjeResponse registrarCanje(CanjeRequest request, String nombreUsuario) {
        if (request.getLoteOrigenId() == null || request.getNumeroLote() == null || request.getNumeroLote().isBlank()
                || request.getCaducidad() == null || request.getUbicaciones() == null || request.getUbicaciones().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Faltan datos del canje (lote de origen, número de lote, caducidad o ubicación)");
        }
        Lote origen = buscarLote(request.getLoteOrigenId());
        if (!"DISPONIBLE".equals(origen.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote de origen ya no está disponible (fue canjeado o apartado en caducados)");
        }
        LocalDate hoy = LocalDate.now();
        if (origen.getCaducidad().isBefore(hoy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote ya caducó. Muévelo al apartado de caducados en lugar de canjearlo");
        }
        long mesesDeVida = mesesDeVida(origen.getCaducidad(), hoy);
        if (mesesDeVida >= MESES_PARA_CANJE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se canjean lotes con menos de " + MESES_PARA_CANJE + " meses de vida. Este tiene " + mesesDeVida);
        }
        String numeroLote = request.getNumeroLote().trim();
        String clave = origen.getMedicamento().getClave();
        if (numeroLote.equalsIgnoreCase(origen.getNumeroLote())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote nuevo debe tener un número distinto al de origen");
        }
        if (!request.getCaducidad().isAfter(origen.getCaducidad())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caducidad del lote nuevo debe ser posterior a la del lote de origen");
        }
        if (loteRepository.existsByMedicamentoClaveAndNumeroLoteIgnoreCase(clave, numeroLote)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote " + numeroLote + " ya existe en el inventario de esta clave");
        }
        validarUbicaciones(request.getUbicaciones());
        Usuario usuario = buscarUsuario(nombreUsuario);

        // CA2: el lote de origen sale completo del inventario disponible.
        List<Existencia> existenciasOrigen = existenciaRepository.findByLoteId(origen.getId());
        int cajasOrigen = 0;
        for (Existencia existencia : existenciasOrigen) {
            if (existencia.getCantidadCajas() <= 0) {
                continue;
            }
            cajasOrigen += existencia.getCantidadCajas();
            registrarMovimiento(existencia, "CANJE", "SALIDA", existencia.getCantidadCajas(), usuario, null);
            existencia.setCantidadCajas(0);
            existenciaRepository.save(existencia);
        }
        origen.setEstatus("CANJEADO");
        loteRepository.save(origen);

        // CA2 y CA3: el lote nuevo queda disponible y ligado al de origen.
        Lote nuevo = new Lote();
        nuevo.setMedicamento(origen.getMedicamento());
        nuevo.setNumeroLote(numeroLote);
        nuevo.setCaducidad(request.getCaducidad());
        nuevo.setProveedor(origen.getProveedor());
        nuevo.setEstatus("DISPONIBLE");
        nuevo.setLoteOrigen(origen);
        nuevo = loteRepository.save(nuevo);

        int cajasNuevas = 0;
        LocalDateTime fecha = null;
        for (UbicacionCantidad ubicacionCantidad : request.getUbicaciones()) {
            Existencia existencia = new Existencia();
            existencia.setLote(nuevo);
            existencia.setUbicacion(ubicacionCantidad.getUbicacion().trim());
            existencia.setCantidadCajas(ubicacionCantidad.getCajas());
            existencia = existenciaRepository.save(existencia);
            fecha = registrarMovimiento(existencia, "CANJE", "ENTRADA", ubicacionCantidad.getCajas(), usuario, null).getFecha();
            cajasNuevas += ubicacionCantidad.getCajas();
        }

        return new CanjeResponse(fecha, usuario.getNombreCompleto(), clave, origen.getMedicamento().getNombreGenerico(),
                origen.getProveedor(), origen.getId(), origen.getNumeroLote(), origen.getCaducidad(), cajasOrigen,
                nuevo.getId(), nuevo.getNumeroLote(), nuevo.getCaducidad(), cajasNuevas, unirUbicaciones(request.getUbicaciones()));
    }

    /** CA3: trazabilidad de todos los canjes (lote de origen -> lote nuevo), los mas recientes primero. */
    public List<CanjeResponse> listarCanjes() {
        List<CanjeResponse> canjes = new ArrayList<>();
        for (Lote nuevo : loteRepository.findByLoteOrigenIsNotNull()) {
            Lote origen = nuevo.getLoteOrigen();
            List<Movimiento> entradas = movimientoRepository.findByExistenciaLoteIdAndTipo(nuevo.getId(), "CANJE");
            List<Movimiento> salidas = movimientoRepository.findByExistenciaLoteIdAndTipo(origen.getId(), "CANJE");
            Movimiento primero = entradas.stream().min(Comparator.comparing(Movimiento::getFecha)).orElse(null);
            canjes.add(new CanjeResponse(
                    primero != null ? primero.getFecha() : null,
                    primero != null ? primero.getUsuario().getNombreCompleto() : null,
                    nuevo.getMedicamento().getClave(),
                    nuevo.getMedicamento().getNombreGenerico(),
                    origen.getProveedor(),
                    origen.getId(),
                    origen.getNumeroLote(),
                    origen.getCaducidad(),
                    salidas.stream().mapToInt(Movimiento::getCantidadCajas).sum(),
                    nuevo.getId(),
                    nuevo.getNumeroLote(),
                    nuevo.getCaducidad(),
                    entradas.stream().mapToInt(Movimiento::getCantidadCajas).sum(),
                    entradas.stream().map(m -> m.getExistencia().getUbicacion()).distinct().collect(Collectors.joining(", "))));
        }
        canjes.sort(Comparator.comparing(CanjeResponse::getFecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return canjes;
    }

    // ---------- HU19: apartado de caducados ----------

    /**
     * pendientes=true: lotes vencidos que siguen en el inventario disponible (solo
     * sugerencia, CA4). pendientes=false: el apartado de caducados (CA3).
     */
    public List<CaducadoResponse> listarCaducados(boolean pendientes) {
        if (pendientes) {
            LocalDate hoy = LocalDate.now();
            return loteRepository.findByEstatus("DISPONIBLE").stream()
                    .filter(lote -> lote.getCaducidad().isBefore(hoy))
                    .map(lote -> {
                        List<UbicacionCantidad> ubicaciones = existenciaRepository.findByLoteId(lote.getId()).stream()
                                .filter(e -> e.getCantidadCajas() > 0)
                                .map(e -> ubicacion(e.getUbicacion(), e.getCantidadCajas()))
                                .toList();
                        return aCaducado(lote, ubicaciones, null, null);
                    })
                    .filter(caducado -> caducado.getCajas() > 0)
                    .sorted(Comparator.comparing(CaducadoResponse::getCaducidad))
                    .toList();
        }
        return loteRepository.findByEstatus("CADUCADO").stream()
                .map(lote -> {
                    List<Movimiento> bajas = movimientoRepository.findByExistenciaLoteIdAndTipo(lote.getId(), "BAJA_CADUCIDAD");
                    List<UbicacionCantidad> ubicaciones = bajas.stream()
                            .map(m -> ubicacion(m.getExistencia().getUbicacion(), m.getCantidadCajas()))
                            .toList();
                    Movimiento primero = bajas.stream().min(Comparator.comparing(Movimiento::getFecha)).orElse(null);
                    return aCaducado(lote, ubicaciones,
                            primero != null ? primero.getFecha() : null,
                            primero != null ? primero.getUsuario().getNombreCompleto() : null);
                })
                .sorted(Comparator.comparing(CaducadoResponse::getFechaApartado, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** CA1, CA2 y CA4: mueve un lote vencido al apartado; solo se llama despues de que el usuario confirma. */
    @Transactional
    public CaducadoResponse moverACaducados(Long loteId, String nombreUsuario) {
        if (loteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el lote que se mueve a caducados");
        }
        Lote lote = buscarLote(loteId);
        if (!"DISPONIBLE".equals(lote.getEstatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote ya no está en el inventario disponible");
        }
        if (!lote.getCaducidad().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote todavía no caduca; solo se apartan lotes vencidos");
        }
        Usuario usuario = buscarUsuario(nombreUsuario);
        List<UbicacionCantidad> ubicaciones = new ArrayList<>();
        LocalDateTime fecha = LocalDateTime.now();
        for (Existencia existencia : existenciaRepository.findByLoteId(lote.getId())) {
            if (existencia.getCantidadCajas() <= 0) {
                continue;
            }
            ubicaciones.add(ubicacion(existencia.getUbicacion(), existencia.getCantidadCajas()));
            fecha = registrarMovimiento(existencia, "BAJA_CADUCIDAD", "SALIDA", existencia.getCantidadCajas(), usuario, null).getFecha();
            existencia.setCantidadCajas(0);
            existenciaRepository.save(existencia);
        }
        if (ubicaciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote no tiene existencia que apartar");
        }
        lote.setEstatus("CADUCADO");
        loteRepository.save(lote);
        return aCaducado(lote, ubicaciones, fecha, usuario.getNombreCompleto());
    }

    // ---------- HU20: prestamos y transferencias ----------

    @Transactional
    public MovimientoResponse registrarExterno(MovimientoExternoRequest request, String nombreUsuario) {
        return servicioMovimientos.aRespuesta(aplicarExterno(request, () -> buscarUsuario(nombreUsuario), null));
    }

    /**
     * Valida y aplica al inventario un movimiento de préstamo o transferencia, y lo
     * deja ligado al expediente si se indica (lo usa ServicioExpedientesExternos para
     * el movimiento inicial y las devoluciones). El usuario se busca después de
     * validar los datos, para responder primero el error de captura.
     */
    @Transactional
    public Movimiento aplicarExterno(MovimientoExternoRequest request, Supplier<Usuario> buscarUsuario, ExpedienteExterno expediente) {
        String tipo = request.getTipo() == null ? "" : request.getTipo().trim().toUpperCase();
        String sentido = request.getSentido() == null ? "" : request.getSentido().trim().toUpperCase();
        if (!tipo.equals("PRESTAMO") && !tipo.equals("TRANSFERENCIA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo debe ser PRESTAMO o TRANSFERENCIA");
        }
        if (!sentido.equals("SALIDA") && !sentido.equals("ENTRADA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El sentido debe ser SALIDA o ENTRADA");
        }
        String institucion = request.getInstitucion() == null ? "" : request.getInstitucion().trim();
        if (institucion.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la institución con la que se hizo el movimiento");
        }
        if (request.getCajas() == null || request.getCajas() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser un número entero mayor a cero");
        }
        Usuario usuario = buscarUsuario.get();
        LocalDate hoy = LocalDate.now();

        Existencia existencia;
        if (sentido.equals("SALIDA")) {
            // CA2: se descuenta de la existencia (lote y ubicacion) elegida.
            if (request.getExistenciaId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige el lote y la ubicación de donde sale el medicamento");
            }
            existencia = existenciaRepository.findById(request.getExistenciaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Existencia no encontrada"));
            Lote lote = existencia.getLote();
            if (!"DISPONIBLE".equals(lote.getEstatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El lote ya no está disponible");
            }
            if (lote.getCaducidad().isBefore(hoy)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se presta ni se transfiere un lote vencido");
            }
            if (request.getCajas() > existencia.getCantidadCajas()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Existencia insuficiente: el lote " + lote.getNumeroLote() + " en " + existencia.getUbicacion()
                                + " solo tiene " + existencia.getCantidadCajas() + " cajas");
            }
            existencia.setCantidadCajas(existencia.getCantidadCajas() - request.getCajas());
        } else {
            // CA2: se suma al lote recibido en la ubicacion indicada.
            String clave = request.getClave() == null ? "" : request.getClave().trim();
            String numeroLote = request.getNumeroLote() == null ? "" : request.getNumeroLote().trim();
            String ubicacion = request.getUbicacion() == null ? "" : request.getUbicacion().trim();
            if (clave.isEmpty() || numeroLote.isEmpty() || request.getCaducidad() == null || ubicacion.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completa clave, lote, caducidad y ubicación de lo que se recibe");
            }
            if (request.getCaducidad().isBefore(hoy)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede recibir un lote que ya caducó");
            }
            Medicamento medicamento = medicamentoRepository.findById(clave)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clave de medicamento no encontrada"));
            Lote lote = loteRepository.findByMedicamentoClaveAndNumeroLoteIgnoreCaseAndEstatus(clave, numeroLote, "DISPONIBLE").stream()
                    .findFirst()
                    .orElse(null);
            if (lote != null && !lote.getCaducidad().equals(request.getCaducidad())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El lote " + numeroLote + " ya existe con caducidad " + lote.getCaducidad() + ". Revisa la caducidad");
            }
            if (lote == null) {
                lote = new Lote();
                lote.setMedicamento(medicamento);
                lote.setNumeroLote(numeroLote);
                lote.setCaducidad(request.getCaducidad());
                lote.setProveedor(institucion);
                lote.setEstatus("DISPONIBLE");
                lote = loteRepository.save(lote);
            }
            Lote loteDestino = lote;
            existencia = existenciaRepository.findByLoteId(loteDestino.getId()).stream()
                    .filter(e -> e.getUbicacion().equalsIgnoreCase(ubicacion))
                    .findFirst()
                    .orElseGet(() -> {
                        Existencia nueva = new Existencia();
                        nueva.setLote(loteDestino);
                        nueva.setUbicacion(ubicacion);
                        nueva.setCantidadCajas(0);
                        return nueva;
                    });
            existencia.setCantidadCajas(existencia.getCantidadCajas() + request.getCajas());
        }
        existencia = existenciaRepository.save(existencia);

        // CA3 y CA4: queda con su propio tipo y la institucion en el historial.
        Movimiento movimiento = registrarMovimiento(existencia, tipo, sentido, request.getCajas(), usuario, institucion);
        if (expediente != null) {
            movimiento.setExpediente(expediente);
            movimiento = movimientoRepository.save(movimiento);
        }
        return movimiento;
    }

    public List<MovimientoResponse> listarExternos() {
        return movimientoRepository.findByTipoInOrderByFechaDesc(List.of("PRESTAMO", "TRANSFERENCIA")).stream()
                .map(servicioMovimientos::aRespuesta)
                .toList();
    }

    // ---------- helpers ----------

    /** Meses entre el mes actual y el de caducidad, igual que en el frontend (negativo si ya vencio). */
    static long mesesDeVida(LocalDate caducidad, LocalDate hoy) {
        return ChronoUnit.MONTHS.between(YearMonth.from(hoy), YearMonth.from(caducidad));
    }

    private void validarUbicaciones(List<UbicacionCantidad> ubicaciones) {
        for (UbicacionCantidad ubicacionCantidad : ubicaciones) {
            if (ubicacionCantidad.getUbicacion() == null || ubicacionCantidad.getUbicacion().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la ubicación del lote nuevo");
            }
            if (ubicacionCantidad.getCajas() == null || ubicacionCantidad.getCajas() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de cajas debe ser mayor a cero");
            }
        }
    }

    private Movimiento registrarMovimiento(Existencia existencia, String tipo, String sentido, int cajas,
                                           Usuario usuario, String institucion) {
        Movimiento movimiento = new Movimiento();
        movimiento.setExistencia(existencia);
        movimiento.setTipo(tipo);
        movimiento.setSentido(sentido);
        movimiento.setCantidadCajas(cajas);
        movimiento.setUsuario(usuario);
        movimiento.setInstitucionExterna(institucion);
        return movimientoRepository.save(movimiento);
    }

    private CaducadoResponse aCaducado(Lote lote, List<UbicacionCantidad> ubicaciones, LocalDateTime fecha, String usuario) {
        return new CaducadoResponse(
                lote.getId(),
                lote.getMedicamento().getClave(),
                lote.getMedicamento().getNombreGenerico(),
                lote.getNumeroLote(),
                lote.getCaducidad(),
                lote.getProveedor(),
                ubicaciones.stream().mapToInt(UbicacionCantidad::getCajas).sum(),
                ubicaciones,
                fecha,
                usuario
        );
    }

    private static UbicacionCantidad ubicacion(String nombre, int cajas) {
        UbicacionCantidad ubicacionCantidad = new UbicacionCantidad();
        ubicacionCantidad.setUbicacion(nombre);
        ubicacionCantidad.setCajas(cajas);
        return ubicacionCantidad;
    }

    private static String unirUbicaciones(List<UbicacionCantidad> ubicaciones) {
        return ubicaciones.stream().map(u -> u.getUbicacion().trim()).distinct().collect(Collectors.joining(", "));
    }

    private Lote buscarLote(Long id) {
        return loteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote no encontrado"));
    }

    private Usuario buscarUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}
