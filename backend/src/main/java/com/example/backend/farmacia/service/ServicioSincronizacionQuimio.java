package com.example.backend.farmacia.service;

import com.example.backend.intelisis.ConexionIntelisis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lee de Intelisis (solo SELECT) las solicitudes de procedimiento a Oncología Médica y las guarda en
 * quimio_solicitud, para que la pantalla de Quimioterapia no consulte Intelisis en cada búsqueda.
 *
 * Validado con datos reales (2026-10-28):
 *  - La solicitud es vsa_AuxTxSol con Servicio = 'ONCOLOGIA MEDICA' (unas 5,500 al año). Su único
 *    renglón (vsa_AuxTxSolD) es el servicio HRAE3251 QUIMIOTERAPIA; los medicamentos solo vienen
 *    escritos en Notas, sin clave (BaseQuimio y QuimioBase nunca se llenan).
 *  - El "Folio" de la hoja impresa es ID_AuxTxSol (la columna Folio casi siempre está vacía).
 *  - ExpKey es Cte.Cliente (el expediente) y MedicoAdscrito es Usuario.Usuario.
 *  - Casi todas quedan SINFIRMAR; las canceladas traen CancelFecha y no se leen.
 *  - Se piden en promedio 18 días antes de la fecha requerida (hasta casi un año): por eso se leen las
 *    de los últimos días configurados y, aunque sean más viejas, las que todavía se requieren.
 */
@Service
public class ServicioSincronizacionQuimio {

    private static final Logger log = LoggerFactory.getLogger(ServicioSincronizacionQuimio.class);
    // Las solicitudes con fecha requerida de hasta estos días atrás se siguen mostrando.
    private static final int DIAS_REQUERIDA_ATRAS = 7;

    /** Columnas de una solicitud (agregar el WHERE). Las usa también la consulta de un folio fuera de la ventana. */
    static final String SELECT_SOLICITUD = """
            SELECT s.ID_AuxTxSol, s.Fecha, s.FechaRequerida, s.Estatus, s.ExpKey, c.Nombre, s.ServicioSolicita, s.Dx, s.Notas,
                   s.MedicoAdscrito, u.Nombre
            FROM vsa_AuxTxSol s WITH (NOLOCK)
            LEFT JOIN Cte c WITH (NOLOCK) ON c.Cliente = s.ExpKey
            LEFT JOIN Usuario u WITH (NOLOCK) ON u.Usuario = s.MedicoAdscrito
            WHERE s.Servicio = 'ONCOLOGIA MEDICA' AND s.CancelFecha IS NULL
            """;

    private final ConexionIntelisis conexion;
    private final JdbcTemplate haero;
    private final TransactionTemplate transaccion;
    private final int dias;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public ServicioSincronizacionQuimio(ConexionIntelisis conexion,
                                        JdbcTemplate haero,
                                        PlatformTransactionManager transactionManager,
                                        @Value("${quimioterapia.dias:60}") int dias) {
        this.conexion = conexion;
        this.haero = haero;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.dias = dias;
    }

    @Scheduled(fixedDelayString = "${quimioterapia.sincronizacion.intervalo:PT5M}",
               initialDelayString = "${quimioterapia.sincronizacion.retraso-inicial:PT1M}")
    public void sincronizarProgramada() {
        if (conexion.disponible()) {
            sincronizar();
        }
    }

    public boolean estaEnCurso() {
        return enCurso.get();
    }

    void sincronizar() {
        if (!enCurso.compareAndSet(false, true)) {
            return;
        }
        Long bitacora = null;
        try {
            bitacora = haero.queryForObject("INSERT INTO quimio_sincronizacion (inicio, estatus) OUTPUT INSERTED.id VALUES (?, 'EN_CURSO')",
                    Long.class, Timestamp.valueOf(LocalDateTime.now()));
            JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new IllegalStateException(conexion.motivoNoDisponible()));

            // 1) Leer de Intelisis, fuera de la transacción de HAERO.
            List<Object[]> solicitudes = intelisis.query(SELECT_SOLICITUD
                            + " AND (s.Fecha >= DATEADD(day, ?, GETDATE()) OR s.FechaRequerida >= DATEADD(day, ?, GETDATE()))",
                    (rs, n) -> fila(rs), -dias, -DIAS_REQUERIDA_ATRAS);

            // 2) Reemplazar la copia de una vez: lo que salió de la ventana se borra (datos clínicos).
            Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
            List<Object[]> filas = solicitudes.stream().map(s -> {
                Object[] conFecha = Arrays.copyOf(s, s.length + 1);
                conFecha[s.length] = ahora;
                return conFecha;
            }).toList();
            transaccion.executeWithoutResult(estado -> {
                haero.update("DELETE FROM quimio_solicitud");
                haero.batchUpdate("""
                        INSERT INTO quimio_solicitud (folio, fecha, fecha_requerida, estatus, expediente, paciente, servicio_solicita,
                                                      diagnostico, notas, medico_usuario, medico, actualizado_en)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, filas);
            });

            haero.update("UPDATE quimio_sincronizacion SET fin = ?, estatus = 'OK', solicitudes = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), solicitudes.size(), bitacora);
            log.info("Quimioterapia: {} solicitudes de Oncología Médica.", solicitudes.size());
        } catch (Exception e) {
            log.warn("Quimioterapia: no se pudo leer Intelisis: {}", e.getMessage());
            if (bitacora != null) {
                String error = String.valueOf(e.getMessage());
                haero.update("UPDATE quimio_sincronizacion SET fin = ?, estatus = 'ERROR', error = ? WHERE id = ?",
                        Timestamp.valueOf(LocalDateTime.now()), error.length() > 1000 ? error.substring(0, 1000) : error, bitacora);
            }
        } finally {
            enCurso.set(false);
        }
    }

    /** Una fila de SELECT_SOLICITUD como valores para quimio_solicitud (sin actualizado_en). */
    static Object[] fila(ResultSet rs) throws SQLException {
        return new Object[]{rs.getInt(1), rs.getTimestamp(2), rs.getTimestamp(3), texto(rs, 4, 20), texto(rs, 5, 30), texto(rs, 6, 250),
                texto(rs, 7, 100), texto(rs, 8, 1000), texto(rs, 9, Integer.MAX_VALUE), texto(rs, 10, 20), texto(rs, 11, 200)};
    }

    private static String texto(ResultSet rs, int columna, int maximo) throws SQLException {
        String valor = rs.getString(columna);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        // Intelisis deja tabuladores y espacios pegados en algunas claves y nombres.
        String limpio = valor.replace('\t', ' ').strip();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }
}
