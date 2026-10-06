package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.DevolucionPrestamoRequest;
import com.example.backend.almacen.dto.DocumentoExternoResponse;
import com.example.backend.almacen.dto.ExpedienteExternoResponse;
import com.example.backend.almacen.dto.MovimientoExternoRequest;
import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.dto.RegistrarExpedienteRequest;
import com.example.backend.almacen.entity.DocumentoExterno;
import com.example.backend.almacen.entity.ExpedienteExterno;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.DocumentoExternoRepository;
import com.example.backend.almacen.repository.ExpedienteExternoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Préstamos y transferencias con expediente (ajuste HU20, en pruebas).
 *
 * Préstamo: se crea con el PDF de solicitud y una fecha límite de devolución.
 * Se le registran devoluciones (parciales o totales) y se cierra con el PDF de
 * cierre cuando ya se devolvió todo. Estatus: ACTIVO -> DEVUELTO_PARCIAL -> CERRADO;
 * VENCIDO y POR_CERRAR se calculan al consultar.
 *
 * Transferencia (esqueleto): se crea con el PDF de envío y se confirma con el PDF
 * de recepción. Estatus: REGISTRADA -> CONFIRMADA.
 *
 * Los movimientos de inventario los aplica ServicioMovimientosEspeciales, con las
 * mismas validaciones que antes (existencia, lote vencido, etc.).
 */
@Service
public class ServicioExpedientesExternos {

    public static final long TAMANO_MAXIMO_PDF = 10L * 1024 * 1024;

    private final ExpedienteExternoRepository expedienteRepository;
    private final DocumentoExternoRepository documentoRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicioMovimientosEspeciales movimientosEspeciales;
    private final ServicioMovimientos servicioMovimientos;

    public ServicioExpedientesExternos(ExpedienteExternoRepository expedienteRepository,
                                       DocumentoExternoRepository documentoRepository,
                                       MovimientoRepository movimientoRepository,
                                       UsuarioRepository usuarioRepository,
                                       ServicioMovimientosEspeciales movimientosEspeciales,
                                       ServicioMovimientos servicioMovimientos) {
        this.expedienteRepository = expedienteRepository;
        this.documentoRepository = documentoRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientosEspeciales = movimientosEspeciales;
        this.servicioMovimientos = servicioMovimientos;
    }

    // ---------- Consulta ----------

    public List<ExpedienteExternoResponse> listar() {
        List<ExpedienteExterno> expedientes = expedienteRepository.findAllByOrderByFechaRegistroDesc();
        if (expedientes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = expedientes.stream().map(ExpedienteExterno::getId).toList();
        Map<Long, List<DocumentoExternoResponse>> documentos = documentoRepository.resumenes(ids).stream()
                .collect(Collectors.groupingBy(DocumentoExternoResponse::getExpedienteId));
        Map<Long, List<MovimientoResponse>> movimientos = movimientoRepository.findByExpedienteIdInOrderByFechaAsc(ids).stream()
                .collect(Collectors.groupingBy(m -> m.getExpediente().getId(),
                        Collectors.mapping(servicioMovimientos::aRespuesta, Collectors.toList())));
        LocalDate hoy = LocalDate.now();
        return expedientes.stream()
                .map(e -> aRespuesta(e, documentos.getOrDefault(e.getId(), List.of()), movimientos.getOrDefault(e.getId(), List.of()), hoy))
                .toList();
    }

    public DocumentoExterno documento(Long expedienteId, Long documentoId) {
        return documentoRepository.findByIdAndExpedienteId(documentoId, expedienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento no encontrado"));
    }

    // ---------- Alta ----------

    @Transactional
    public ExpedienteExternoResponse registrar(RegistrarExpedienteRequest datos, MultipartFile documento, String nombreUsuario) {
        if (datos == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos del movimiento");
        }
        String tipo = normalizar(datos.getTipo());
        boolean esPrestamo = tipo.equals("PRESTAMO");
        if (esPrestamo) {
            if (datos.getFechaLimite() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fecha límite de devolución del préstamo");
            }
            if (datos.getFechaLimite().isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha límite de devolución no puede ser anterior a hoy");
            }
        }
        // Sin el documento no se crea: el PDF de solicitud (préstamo) o de envío (transferencia).
        byte[] pdf = leerPdf(documento, esPrestamo ? "el oficio de solicitud del préstamo" : "el documento de la transferencia");
        Usuario usuario = buscarUsuario(nombreUsuario);

        ExpedienteExterno expediente = new ExpedienteExterno();
        expediente.setTipo(tipo);
        expediente.setSentido(normalizar(datos.getSentido()));
        expediente.setInstitucion(datos.getInstitucion() == null ? "" : datos.getInstitucion().trim());
        expediente.setCajas(datos.getCajas());
        expediente.setFechaLimite(esPrestamo ? datos.getFechaLimite() : null);
        expediente.setEstatus(esPrestamo ? "ACTIVO" : "REGISTRADA");
        expediente.setObservaciones(textoOpcional(datos.getObservaciones()));
        expediente.setUsuario(usuario);

        // El movimiento valida tipo, sentido, institución, cajas y existencia; si
        // falla, la transacción se revierte y no queda el expediente a medias.
        Movimiento movimiento = movimientosEspeciales.aplicarExterno(datos, () -> usuario, null);
        expediente.setMedicamento(movimiento.getExistencia().getLote().getMedicamento());
        expediente = expedienteRepository.save(expediente);
        movimiento.setExpediente(expediente);
        movimientoRepository.save(movimiento);

        guardarDocumento(expediente, esPrestamo ? "SOLICITUD" : "ENVIO", documento, pdf, usuario);
        return respuesta(expediente);
    }

    // ---------- Préstamos: devoluciones y cierre ----------

    @Transactional
    public ExpedienteExternoResponse registrarDevolucion(Long expedienteId, DevolucionPrestamoRequest request, String nombreUsuario) {
        ExpedienteExterno expediente = buscarExpediente(expedienteId);
        if (!expediente.getTipo().equals("PRESTAMO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo los préstamos tienen devoluciones");
        }
        if (expediente.getEstatus().equals("CERRADO")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El préstamo ya está cerrado");
        }
        int pendientes = expediente.getCajas() - expediente.getCajasDevueltas();
        if (request.getCajas() == null || request.getCajas() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad devuelta debe ser un número entero mayor a cero");
        }
        if (request.getCajas() > pendientes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo faltan " + pendientes + " cajas por devolver en este préstamo");
        }

        // La devolución va en sentido contrario al préstamo y con la misma clave.
        MovimientoExternoRequest movimiento = new MovimientoExternoRequest();
        movimiento.setTipo("PRESTAMO");
        movimiento.setSentido(expediente.getSentido().equals("SALIDA") ? "ENTRADA" : "SALIDA");
        movimiento.setInstitucion(expediente.getInstitucion());
        movimiento.setCajas(request.getCajas());
        movimiento.setExistenciaId(request.getExistenciaId());
        movimiento.setClave(expediente.getMedicamento().getClave());
        movimiento.setNumeroLote(request.getNumeroLote());
        movimiento.setCaducidad(request.getCaducidad());
        movimiento.setUbicacion(request.getUbicacion());
        Movimiento aplicado = movimientosEspeciales.aplicarExterno(movimiento, () -> buscarUsuario(nombreUsuario), expediente);
        if (!aplicado.getExistencia().getLote().getMedicamento().getClave().equals(expediente.getMedicamento().getClave())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La devolución debe ser de la misma clave que se prestó");
        }

        expediente.setCajasDevueltas(expediente.getCajasDevueltas() + request.getCajas());
        expediente.setEstatus("DEVUELTO_PARCIAL");
        return respuesta(expedienteRepository.save(expediente));
    }

    /**
     * Préstamo: lo cierra con el PDF de cierre, solo si ya se devolvió todo.
     * Transferencia: la confirma con el PDF de recepción.
     */
    @Transactional
    public ExpedienteExternoResponse cerrar(Long expedienteId, MultipartFile documento, String nombreUsuario) {
        ExpedienteExterno expediente = buscarExpediente(expedienteId);
        boolean esPrestamo = expediente.getTipo().equals("PRESTAMO");
        if (esPrestamo) {
            if (expediente.getEstatus().equals("CERRADO")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El préstamo ya está cerrado");
            }
            int pendientes = expediente.getCajas() - expediente.getCajasDevueltas();
            if (pendientes > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se puede cerrar: faltan " + pendientes + " cajas por devolver");
            }
        } else if (expediente.getEstatus().equals("CONFIRMADA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La transferencia ya está confirmada");
        }
        byte[] pdf = leerPdf(documento, esPrestamo ? "el documento de cierre del préstamo" : "el documento de recepción de la transferencia");
        Usuario usuario = buscarUsuario(nombreUsuario);

        expediente.setEstatus(esPrestamo ? "CERRADO" : "CONFIRMADA");
        expediente.setFechaCierre(LocalDateTime.now());
        expediente = expedienteRepository.save(expediente);
        guardarDocumento(expediente, esPrestamo ? "CIERRE" : "RECEPCION", documento, pdf, usuario);
        return respuesta(expediente);
    }

    // ---------- helpers ----------

    /** Estatus para mostrar: VENCIDO si pasó la fecha límite sin cerrarse; POR_CERRAR si ya se devolvió todo. */
    static String estatusVigente(ExpedienteExterno expediente, LocalDate hoy) {
        if (!expediente.getTipo().equals("PRESTAMO") || expediente.getEstatus().equals("CERRADO")) {
            return expediente.getEstatus();
        }
        if (expediente.getCajasDevueltas() >= expediente.getCajas()) {
            return "POR_CERRAR";
        }
        if (expediente.getFechaLimite() != null && expediente.getFechaLimite().isBefore(hoy)) {
            return "VENCIDO";
        }
        return expediente.getEstatus();
    }

    /** Valida que el archivo sea un PDF de máximo 10 MB y devuelve su contenido. */
    static byte[] leerPdf(MultipartFile archivo, String descripcion) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta " + descripcion + " en PDF");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_PDF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PDF no puede pesar más de 10 MB");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo adjunto");
        }
        // Todo PDF empieza con "%PDF"; así no se acepta otro archivo con la extensión cambiada.
        if (contenido.length < 4 || !Arrays.equals(Arrays.copyOf(contenido, 4), new byte[]{'%', 'P', 'D', 'F'})) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser un PDF");
        }
        return contenido;
    }

    private void guardarDocumento(ExpedienteExterno expediente, String tipo, MultipartFile archivo, byte[] contenido, Usuario usuario) {
        String nombre = archivo.getOriginalFilename() == null || archivo.getOriginalFilename().isBlank()
                ? tipo.toLowerCase() + ".pdf"
                : archivo.getOriginalFilename().trim();
        DocumentoExterno documento = new DocumentoExterno();
        documento.setExpediente(expediente);
        documento.setTipo(tipo);
        documento.setNombreArchivo(nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre);
        documento.setTamano(contenido.length);
        documento.setContenido(contenido);
        documento.setUsuario(usuario);
        documentoRepository.save(documento);
    }

    private ExpedienteExternoResponse respuesta(ExpedienteExterno expediente) {
        List<Long> ids = List.of(expediente.getId());
        return aRespuesta(expediente,
                documentoRepository.resumenes(ids),
                movimientoRepository.findByExpedienteIdInOrderByFechaAsc(ids).stream().map(servicioMovimientos::aRespuesta).toList(),
                LocalDate.now());
    }

    private ExpedienteExternoResponse aRespuesta(ExpedienteExterno e, List<DocumentoExternoResponse> documentos,
                                                 List<MovimientoResponse> movimientos, LocalDate hoy) {
        Medicamento medicamento = e.getMedicamento();
        return new ExpedienteExternoResponse(
                e.getId(),
                e.getTipo(),
                e.getSentido(),
                e.getInstitucion(),
                medicamento.getClave(),
                medicamento.getNombreGenerico(),
                e.getCajas(),
                e.getCajasDevueltas(),
                e.getFechaRegistro(),
                e.getFechaLimite(),
                e.getFechaCierre(),
                e.getEstatus(),
                estatusVigente(e, hoy),
                e.getObservaciones(),
                e.getUsuario().getNombreCompleto(),
                documentos,
                movimientos
        );
    }

    private ExpedienteExterno buscarExpediente(Long id) {
        return expedienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo o transferencia no encontrado"));
    }

    private Usuario buscarUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase();
    }

    private static String textoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
