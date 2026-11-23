package com.example.backend.almacen.service;

import com.example.backend.almacen.entity.AlmacenBajaDefinitiva;
import com.example.backend.almacen.entity.AlmacenBajaDefinitivaLote;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.repository.AlmacenBajaDefinitivaLoteRepository;
import com.example.backend.almacen.repository.AlmacenBajaDefinitivaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.usuarios.entity.Usuario;
import com.example.backend.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Baja definitiva de caducados (ajuste de Supervisión). Los lotes apartados en caducados (HU19)
 * siguen siendo bienes del hospital; salen del inventario solo cuando la empresa los recoge y se
 * levanta el acta. Se registra el acta con su PDF y los lotes pasan a BAJA_DEFINITIVA.
 */
@Service
public class ServicioBajaDefinitiva {

    static final String BAJA_DEFINITIVA = "BAJA_DEFINITIVA";
    private static final long TAMANO_MAXIMO_PDF = 10L * 1024 * 1024;

    private final AlmacenBajaDefinitivaRepository bajas;
    private final AlmacenBajaDefinitivaLoteRepository lotesDeBaja;
    private final LoteRepository lotes;
    private final MovimientoRepository movimientos;
    private final UsuarioRepository usuarioRepository;

    public ServicioBajaDefinitiva(AlmacenBajaDefinitivaRepository bajas, AlmacenBajaDefinitivaLoteRepository lotesDeBaja,
                                  LoteRepository lotes, MovimientoRepository movimientos, UsuarioRepository usuarioRepository) {
        this.bajas = bajas;
        this.lotesDeBaja = lotesDeBaja;
        this.lotes = lotes;
        this.movimientos = movimientos;
        this.usuarioRepository = usuarioRepository;
    }

    public record RegistrarBajaDefinitiva(List<Long> loteIds, String folioActa, String empresa, LocalDate fechaRecoleccion,
                                          String observaciones) {
    }

    public record LoteDadoDeBaja(Long loteId, String clave, String medicamento, String numeroLote, LocalDate caducidad, int cajas) {
    }

    public record BajaDefinitiva(Long id, String folioActa, String empresa, LocalDate fechaRecoleccion, String observaciones,
                                 String documento, LocalDateTime fecha, String usuario, List<LoteDadoDeBaja> lotes) {
    }

    public record Documento(String nombre, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public List<BajaDefinitiva> listar() {
        List<AlmacenBajaDefinitivaRepository.Resumen> resumenes = bajas.resumenes();
        Map<Long, List<LoteDadoDeBaja>> lotesPorBaja = new HashMap<>();
        if (!resumenes.isEmpty()) {
            for (AlmacenBajaDefinitivaLote bl : lotesDeBaja.deLasBajas(resumenes.stream().map(AlmacenBajaDefinitivaRepository.Resumen::getId).toList())) {
                Lote l = bl.getLote();
                lotesPorBaja.computeIfAbsent(bl.getBajaId(), k -> new ArrayList<>()).add(new LoteDadoDeBaja(l.getId(),
                        l.getMedicamento().getClave(), l.getMedicamento().getNombreGenerico(), l.getNumeroLote(), l.getCaducidad(), bl.getCajas()));
            }
        }
        return resumenes.stream().map(b -> new BajaDefinitiva(b.getId(), b.getFolioActa(), b.getEmpresa(), b.getFechaRecoleccion(),
                b.getObservaciones(), b.getDocumentoNombre(), b.getFecha(), b.getUsuario(), lotesPorBaja.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Documento documento(Long id) {
        return bajas.findById(id).map(b -> new Documento(b.getDocumentoNombre(), b.getDocumento()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa baja definitiva no existe"));
    }

    @Transactional
    public BajaDefinitiva registrar(RegistrarBajaDefinitiva datos, MultipartFile acta, String nombreUsuario) {
        if (datos == null || datos.loteIds() == null || datos.loteIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige los lotes caducados que se llevó la empresa");
        }
        String folio = obligatorio(datos.folioActa(), "Escribe el folio del acta", 60);
        String empresa = obligatorio(datos.empresa(), "Escribe la empresa que recogió los caducados", 150);
        if (datos.fechaRecoleccion() == null || datos.fechaRecoleccion().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fecha en que se recogieron (no puede ser futura)");
        }
        String observaciones = datos.observaciones() == null || datos.observaciones().isBlank() ? null : datos.observaciones().trim();
        if (observaciones != null && observaciones.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las observaciones no pueden pasar de 500 caracteres");
        }
        byte[] pdf = leerPdf(acta);
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Map<Lote, Integer> elegidos = new LinkedHashMap<>();
        for (Long loteId : new LinkedHashSet<>(datos.loteIds())) {
            // Candado del lote: dos personas no pueden darlo de baja a la vez.
            Lote lote = lotes.bloquear(loteId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El lote " + loteId + " no existe"));
            if (!"CADUCADO".equals(lote.getEstatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se dan de baja definitiva los lotes apartados en caducados");
            }
            elegidos.put(lote, movimientos.cajasEnCaducados(loteId));
        }

        String nombre = acta.getOriginalFilename() == null || acta.getOriginalFilename().isBlank() ? "acta.pdf" : acta.getOriginalFilename();
        if (nombre.length() > 255) {
            nombre = nombre.substring(nombre.length() - 255);
        }
        AlmacenBajaDefinitiva baja = new AlmacenBajaDefinitiva();
        baja.setFolioActa(folio);
        baja.setEmpresa(empresa);
        baja.setFechaRecoleccion(datos.fechaRecoleccion());
        baja.setObservaciones(observaciones);
        baja.setDocumentoNombre(nombre);
        baja.setDocumento(pdf);
        baja.setFecha(LocalDateTime.now());
        baja.setUsuario(usuario);
        bajas.save(baja);
        elegidos.forEach((lote, cajas) -> {
            AlmacenBajaDefinitivaLote renglon = new AlmacenBajaDefinitivaLote();
            renglon.setBajaId(baja.getId());
            renglon.setLoteId(lote.getId());
            // Las relaciones son de solo lectura (las columnas las escriben los ids), pero se llenan para
            // que el listado de abajo, en esta misma transacción, encuentre el lote y la baja en memoria.
            renglon.setBaja(baja);
            renglon.setLote(lote);
            renglon.setCajas(cajas);
            lotesDeBaja.save(renglon);
            lote.setEstatus(BAJA_DEFINITIVA);
        });
        // Se escribe ya, para que el listado de abajo (consulta JPQL) lea lo recién guardado.
        bajas.flush();
        return listar().stream().filter(b -> b.id().equals(baja.getId())).findFirst().orElseThrow();
    }

    private static String obligatorio(String valor, String mensaje, int largo) {
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        String limpio = valor.trim();
        if (limpio.length() > largo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El texto no puede pasar de " + largo + " caracteres");
        }
        return limpio;
    }

    /** El acta en PDF (máximo 10 MB, que de verdad empiece con "%PDF"). */
    private static byte[] leerPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjunta el acta de baja en PDF");
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
        if (contenido.length < 4 || !Arrays.equals(Arrays.copyOf(contenido, 4), new byte[]{'%', 'P', 'D', 'F'})) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser un PDF");
        }
        return contenido;
    }
}
