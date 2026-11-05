package com.example.backend.almacen.service;

import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baja definitiva de caducados (ajuste de Supervisión). Los lotes apartados en caducados (HU19)
 * siguen siendo bienes del hospital; salen del inventario solo cuando la empresa los recoge y se
 * levanta el acta. Se registra el acta con su PDF y los lotes pasan a BAJA_DEFINITIVA.
 */
@Service
public class ServicioBajaDefinitiva {

    static final String BAJA_DEFINITIVA = "BAJA_DEFINITIVA";
    private static final long TAMANO_MAXIMO_PDF = 10L * 1024 * 1024;

    private final JdbcTemplate haero;
    private final UsuarioRepository usuarioRepository;

    public ServicioBajaDefinitiva(JdbcTemplate haero, UsuarioRepository usuarioRepository) {
        this.haero = haero;
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

    public List<BajaDefinitiva> listar() {
        List<Map<String, Object>> filas = haero.queryForList("""
                SELECT b.id, b.folio_acta, b.empresa, b.fecha_recoleccion, b.observaciones, b.documento_nombre, b.fecha, u.nombre_completo
                FROM almacen_baja_definitiva b JOIN usuario u ON u.id = b.usuario_id ORDER BY b.fecha DESC
                """);
        List<BajaDefinitiva> bajas = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            long id = ((Number) f.get("id")).longValue();
            bajas.add(new BajaDefinitiva(id, (String) f.get("folio_acta"), (String) f.get("empresa"),
                    ((java.sql.Date) f.get("fecha_recoleccion")).toLocalDate(), (String) f.get("observaciones"),
                    (String) f.get("documento_nombre"), ((Timestamp) f.get("fecha")).toLocalDateTime(),
                    (String) f.get("nombre_completo"), lotes(id)));
        }
        return bajas;
    }

    public Documento documento(Long id) {
        return haero.query("SELECT documento_nombre, documento FROM almacen_baja_definitiva WHERE id = ?",
                        (rs, n) -> new Documento(rs.getString(1), rs.getBytes(2)), id)
                .stream().findFirst()
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

        Set<Long> ids = new LinkedHashSet<>(datos.loteIds());
        List<Object[]> lotes = new ArrayList<>();
        for (Long loteId : ids) {
            // Bloqueo del lote: dos personas no pueden darlo de baja a la vez.
            List<String> estatus = haero.queryForList("SELECT estatus FROM lote WITH (UPDLOCK, ROWLOCK) WHERE id = ?", String.class, loteId);
            if (estatus.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El lote " + loteId + " no existe");
            }
            if (!"CADUCADO".equals(estatus.get(0))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se dan de baja definitiva los lotes apartados en caducados");
            }
            Integer cajas = haero.queryForObject("""
                    SELECT COALESCE(SUM(m.cantidad_cajas), 0) FROM movimiento m JOIN existencia e ON e.id = m.existencia_id
                    WHERE e.lote_id = ? AND m.tipo = 'BAJA_CADUCIDAD'
                    """, Integer.class, loteId);
            lotes.add(new Object[]{loteId, cajas == null ? 0 : cajas});
        }

        String nombre = acta.getOriginalFilename() == null || acta.getOriginalFilename().isBlank() ? "acta.pdf" : acta.getOriginalFilename();
        if (nombre.length() > 255) {
            nombre = nombre.substring(nombre.length() - 255);
        }
        Long id = haero.queryForObject("""
                INSERT INTO almacen_baja_definitiva (folio_acta, empresa, fecha_recoleccion, observaciones, documento_nombre, documento, fecha, usuario_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, Long.class, folio, empresa, java.sql.Date.valueOf(datos.fechaRecoleccion()), observaciones, nombre, pdf,
                Timestamp.valueOf(LocalDateTime.now()), usuario.getId());
        for (Object[] lote : lotes) {
            haero.update("INSERT INTO almacen_baja_definitiva_lote (baja_id, lote_id, cajas) VALUES (?, ?, ?)", id, lote[0], lote[1]);
            haero.update("UPDATE lote SET estatus = ? WHERE id = ?", BAJA_DEFINITIVA, lote[0]);
        }
        return listar().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    private List<LoteDadoDeBaja> lotes(long bajaId) {
        return haero.query("""
                SELECT l.id, l.medicamento_clave, m.nombre_generico, l.numero_lote, l.caducidad, bl.cajas
                FROM almacen_baja_definitiva_lote bl JOIN lote l ON l.id = bl.lote_id JOIN medicamento m ON m.clave = l.medicamento_clave
                WHERE bl.baja_id = ? ORDER BY l.medicamento_clave, l.numero_lote
                """, (rs, n) -> new LoteDadoDeBaja(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getDate(5).toLocalDate(), rs.getInt(6)), bajaId);
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
