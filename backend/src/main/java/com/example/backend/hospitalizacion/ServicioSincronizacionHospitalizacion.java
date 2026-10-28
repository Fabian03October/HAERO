package com.example.backend.hospitalizacion;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lee de Intelisis (solo SELECT) las camas, los internados y los medicamentos de sus
 * indicaciones, y los compara con la foto anterior guardada en HAERO para registrar las
 * novedades que Intelisis no avisa.
 *
 * Validado con datos reales (2026-10-27):
 *  - Las camas activas de cada área están en vsa_CenEnfEspacio (CenEnf = central de
 *    enfermería) con su nombre y tipo en Espacio.
 *  - Quién está en cada cama: vsa_IngArea (Activo = 1 y sin FechaEgreso) ligado por
 *    ID_IngHosp a vsa_Ingreso.ID; el paciente es Cte.Cliente = vsa_Ingreso.ExpKey.
 *    vsa_IngHosp ya no se usa. Hay estancias "activas" cuyo ingreso ya tiene EgresoFecha o
 *    está cancelado: no se cuentan.
 *  - Medicamentos: vsa_IndMedicaMedicamento (Activo, SuspendeFecha) de las vsa_IndMedica del
 *    ingreso. No se leen diagnósticos ni indicaciones generales.
 */
@Service
public class ServicioSincronizacionHospitalizacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioSincronizacionHospitalizacion.class);
    // Los medicamentos suspendidos en estos días se siguen leyendo para detectar la suspensión.
    private static final int DIAS_SUSPENDIDOS = 2;

    static final String INGRESO = "INGRESO";
    static final String EGRESO = "EGRESO";
    static final String CAMBIO_CAMA = "CAMBIO_CAMA";
    static final String MEDICAMENTO_NUEVO = "MEDICAMENTO_NUEVO";
    static final String MEDICAMENTO_SUSPENDIDO = "MEDICAMENTO_SUSPENDIDO";
    // Cambió la dosis, la vía o la frecuencia de un medicamento que ya tenía (solo se avisa).
    static final String MEDICAMENTO_MODIFICADO = "MEDICAMENTO_MODIFICADO";

    private static final String INTERNADOS = """
            FROM vsa_IngArea a WITH (NOLOCK)
            JOIN vsa_Ingreso i WITH (NOLOCK) ON i.ID = a.ID_IngHosp
            WHERE a.Activo = 1 AND a.FechaEgreso IS NULL AND i.EgresoFecha IS NULL AND i.FechaCancelado IS NULL
            """;

    private final ConexionIntelisis conexion;
    private final JdbcTemplate haero;
    private final TransactionTemplate transaccion;
    private final int diasRetencion;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public ServicioSincronizacionHospitalizacion(ConexionIntelisis conexion,
                                                 JdbcTemplate haero,
                                                 PlatformTransactionManager transactionManager,
                                                 @Value("${hospitalizacion.dias-retencion:30}") int diasRetencion) {
        this.conexion = conexion;
        this.haero = haero;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.diasRetencion = diasRetencion;
    }

    @Scheduled(fixedDelayString = "${hospitalizacion.sincronizacion.intervalo:PT5M}",
               initialDelayString = "${hospitalizacion.sincronizacion.retraso-inicial:PT1M}")
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
        LocalDateTime inicio = LocalDateTime.now();
        Long bitacora = null;
        try {
            bitacora = abrirBitacora(inicio);
            JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new IllegalStateException(conexion.motivoNoDisponible()));

            // 1) Leer de Intelisis, fuera de la transacción de HAERO.
            List<Object[]> camas = intelisis.query("""
                    SELECT ce.Espacio, e.Nombre, e.Tipo, ce.CenEnf
                    FROM vsa_CenEnfEspacio ce WITH (NOLOCK)
                    LEFT JOIN Espacio e WITH (NOLOCK) ON e.Espacio = ce.Espacio
                    WHERE ce.Activo = 1 AND (e.Estatus IS NULL OR e.Estatus = 'ALTA')
                    """, (rs, n) -> new Object[]{texto(rs, 1), texto(rs, 2), texto(rs, 3), texto(rs, 4)});
            Map<Integer, Estancia> estancias = new LinkedHashMap<>();
            intelisis.query("SELECT a.ID_IngArea, a.ID_IngHosp, a.Espacio, a.Servicio, a.FechaIngreso, i.ExpKey, c.Nombre "
                    + INTERNADOS.replace("WHERE", "LEFT JOIN Cte c WITH (NOLOCK) ON c.Cliente = i.ExpKey WHERE"), rs -> {
                Timestamp fecha = rs.getTimestamp(5);
                estancias.put(rs.getInt(1), new Estancia(rs.getInt(1), rs.getInt(2), texto(rs, 3), texto(rs, 4),
                        fecha == null ? null : fecha.toLocalDateTime(), texto(rs, 6), texto(rs, 7)));
            });
            Map<Integer, Medicamento> medicamentos = new LinkedHashMap<>();
            intelisis.query("""
                    SELECT d.ID_IndMedicaMedicamento, d.ID_IndMedica, m.ID_IngHosp, d.Articulo, d.Medicamento, d.Via, d.Dosis,
                           d.Unidad, d.Cada, d.CadaUnidad, d.Fecha, d.Activo, d.SuspendeFecha,
                           d.Nota, d.ID_IndMedica, d.Externo, d.DosisPara24Horas, d.Unidosis, d.Inicia,
                           CASE WHEN d.InfusionContinua = 1 OR d.Solucion = 1 THEN 1 ELSE 0 END, d.CantidadHora, d.NumeroHoras,
                           COALESCE(d.Usuario, m.Usuario), d.SuspendeUsuario
                    FROM vsa_IndMedicaMedicamento d WITH (NOLOCK)
                    JOIN vsa_IndMedica m WITH (NOLOCK) ON m.ID_IndMedica = d.ID_IndMedica
                    WHERE m.ID_IngHosp IN (SELECT a.ID_IngHosp\s""" + INTERNADOS + """
                    ) AND (d.Activo = 1 OR d.SuspendeFecha >= DATEADD(day, ?, GETDATE()))
                    """, rs -> {
                Timestamp indicado = rs.getTimestamp(11);
                Timestamp suspendido = rs.getTimestamp(13);
                float dosis = rs.getFloat(7);
                boolean sinDosis = rs.wasNull();
                int cada = rs.getInt(9);
                boolean sinCada = rs.wasNull();
                int folio = rs.getInt(15);
                boolean sinFolio = rs.wasNull();
                int dosis24 = rs.getInt(17);
                boolean sinDosis24 = rs.wasNull();
                Timestamp inicia = rs.getTimestamp(19);
                float cantidadHora = rs.getFloat(21);
                boolean sinCantidadHora = rs.wasNull();
                int numeroHoras = rs.getInt(22);
                boolean sinNumeroHoras = rs.wasNull();
                medicamentos.put(rs.getInt(1), new Medicamento(rs.getInt(1), rs.getInt(2), rs.getInt(3), texto(rs, 4), limpiarNombre(texto(rs, 5)),
                        texto(rs, 6), sinDosis ? null : dosis, texto(rs, 8), sinCada ? null : cada, texto(rs, 10),
                        indicado == null ? null : indicado.toLocalDateTime(), rs.getBoolean(12),
                        suspendido == null ? null : suspendido.toLocalDateTime(),
                        new Detalle(texto(rs, 14), sinFolio ? null : folio, rs.getBoolean(16), sinDosis24 ? null : dosis24, rs.getBoolean(18),
                                inicia == null ? null : inicia.toLocalDateTime(), rs.getInt(20) == 1,
                                sinCantidadHora ? null : cantidadHora, sinNumeroHoras ? null : numeroHoras, texto(rs, 23), texto(rs, 24))));
            }, -DIAS_SUSPENDIDOS);

            // Médicos que indicaron o suspendieron (nombre, cédula y especialidad para la hoja y los avisos).
            Set<String> usuarios = new HashSet<>();
            medicamentos.values().forEach(m -> {
                if (m.detalle().indico() != null) usuarios.add(m.detalle().indico());
                if (m.detalle().suspendio() != null) usuarios.add(m.detalle().suspendio());
            });
            List<Object[]> medicos = leerMedicos(intelisis, usuarios);

            // 2) Comparar con la foto anterior y guardar todo de una vez.
            int[] eventos = new int[1];
            transaccion.executeWithoutResult(estado -> {
                boolean primeraVez = haero.queryForObject("SELECT COUNT(*) FROM hosp_sincronizacion WHERE estatus = 'OK'", Integer.class) == 0;
                LocalDateTime ahora = LocalDateTime.now();
                Map<String, String> centralDeCama = guardarCamas(camas, ahora);
                guardarMedicos(medicos, ahora);
                // Pacientes que ya estaban internados: solo de ellos se avisan los medicamentos;
                // los de un recién ingresado van dentro del aviso de ingreso.
                Set<Integer> internadosAntes = new HashSet<>(haero.queryForList(
                        "SELECT DISTINCT id_ingreso FROM hosp_estancia WHERE activa = 1", Integer.class));
                eventos[0] += guardarEstancias(estancias, centralDeCama, ahora, primeraVez);
                eventos[0] += guardarMedicamentos(medicamentos, estancias, centralDeCama, ahora, primeraVez, internadosAntes);
                borrarVencidos(ahora);
            });

            haero.update("UPDATE hosp_sincronizacion SET fin = ?, estatus = 'OK', camas = ?, estancias = ?, medicamentos = ?, eventos = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), camas.size(), estancias.size(), medicamentos.size(), eventos[0], bitacora);
            log.info("Hospitalización: {} camas, {} internados, {} medicamentos, {} novedades.",
                    camas.size(), estancias.size(), medicamentos.size(), eventos[0]);
        } catch (Exception e) {
            log.warn("Hospitalización: no se pudo leer Intelisis: {}", e.getMessage());
            if (bitacora != null) {
                haero.update("UPDATE hosp_sincronizacion SET fin = ?, estatus = 'ERROR', error = ? WHERE id = ?",
                        Timestamp.valueOf(LocalDateTime.now()), recortar(String.valueOf(e.getMessage()), 1000), bitacora);
            }
        } finally {
            enCurso.set(false);
        }
    }

    private Map<String, String> guardarCamas(List<Object[]> camas, LocalDateTime ahora) {
        haero.update("DELETE FROM hosp_cama");
        Map<String, String> central = new HashMap<>();
        List<Object[]> filas = new ArrayList<>();
        for (Object[] cama : camas) {
            if (cama[0] == null || cama[3] == null || central.putIfAbsent((String) cama[0], (String) cama[3]) != null) {
                continue;
            }
            filas.add(new Object[]{cama[0], cama[1], cama[2], cama[3], Timestamp.valueOf(ahora)});
        }
        haero.batchUpdate("INSERT INTO hosp_cama (espacio, nombre, tipo, central, actualizado_en) VALUES (?, ?, ?, ?, ?)", filas);
        return central;
    }

    /** Altas, bajas y cambios de cama comparando con las estancias activas anteriores. */
    private int guardarEstancias(Map<Integer, Estancia> nuevas, Map<String, String> central, LocalDateTime ahora, boolean primeraVez) {
        Map<Integer, String[]> anteriores = new HashMap<>();
        haero.query("SELECT id_ing_area, id_ingreso, espacio FROM hosp_estancia WHERE activa = 1",
                rs -> {
                    anteriores.put(rs.getInt(1), new String[]{String.valueOf(rs.getInt(2)), rs.getString(3)});
                });
        Map<Integer, String> camaAnteriorDelIngreso = new HashMap<>();
        anteriores.forEach((idArea, a) -> {
            if (!nuevas.containsKey(idArea)) {
                camaAnteriorDelIngreso.put(Integer.parseInt(a[0]), a[1]);
            }
        });

        int eventos = 0;
        Set<Integer> ingresosQueCambiaron = new HashSet<>();
        for (Estancia e : nuevas.values()) {
            Timestamp t = Timestamp.valueOf(ahora);
            int cambiadas = haero.update("""
                    UPDATE hosp_estancia SET id_ingreso = ?, espacio = ?, servicio = ?, expediente = ?, paciente = ?, fecha_ingreso = ?,
                           activa = 1, egreso_detectado = NULL, actualizado_en = ?
                    WHERE id_ing_area = ?
                    """, e.idIngreso, e.espacio, e.servicio, e.expediente, e.paciente,
                    e.fechaIngreso == null ? null : Timestamp.valueOf(e.fechaIngreso), t, e.idArea);
            if (cambiadas == 0) {
                haero.update("""
                        INSERT INTO hosp_estancia (id_ing_area, id_ingreso, espacio, servicio, expediente, paciente, fecha_ingreso, activa, actualizado_en)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
                        """, e.idArea, e.idIngreso, e.espacio, e.servicio, e.expediente, e.paciente,
                        e.fechaIngreso == null ? null : Timestamp.valueOf(e.fechaIngreso), t);
            }
            if (!primeraVez && !anteriores.containsKey(e.idArea)) {
                String camaAnterior = camaAnteriorDelIngreso.get(e.idIngreso);
                if (camaAnterior != null && !Objects.equals(camaAnterior, e.espacio)) {
                    ingresosQueCambiaron.add(e.idIngreso);
                    eventos += evento(ahora, CAMBIO_CAMA, e.idIngreso, e.espacio, central,
                            "Llegó de la cama " + camaAnterior + (e.servicio != null ? " · " + e.servicio : ""));
                    eventos += evento(ahora, CAMBIO_CAMA, e.idIngreso, camaAnterior, central, "Se cambió a la cama " + e.espacio);
                } else {
                    eventos += evento(ahora, INGRESO, e.idIngreso, e.espacio, central,
                            "Ingresó a la cama" + (e.servicio != null ? " · " + e.servicio : ""));
                }
            }
        }
        for (Map.Entry<Integer, String[]> a : anteriores.entrySet()) {
            if (nuevas.containsKey(a.getKey())) {
                continue;
            }
            int idIngreso = Integer.parseInt(a.getValue()[0]);
            haero.update("UPDATE hosp_estancia SET activa = 0, egreso_detectado = ?, actualizado_en = ? WHERE id_ing_area = ?",
                    Timestamp.valueOf(ahora), Timestamp.valueOf(ahora), a.getKey());
            if (!primeraVez && !ingresosQueCambiaron.contains(idIngreso)) {
                eventos += evento(ahora, EGRESO, idIngreso, a.getValue()[1], central, "Egresó; la cama quedó libre");
            }
        }
        return eventos;
    }

    /** Medicamentos nuevos y suspendidos. Los de un paciente recién ingresado no generan aviso aparte. */
    private int guardarMedicamentos(Map<Integer, Medicamento> nuevos, Map<Integer, Estancia> estancias,
                                    Map<String, String> central, LocalDateTime ahora, boolean primeraVez,
                                    Set<Integer> internadosAntes) {
        Map<Integer, Boolean> anteriores = new HashMap<>();
        Map<Integer, String> posologiaAnterior = new HashMap<>();
        haero.query("SELECT id, activo, dosis, unidad, via, cada, cada_unidad FROM hosp_medicamento", rs -> {
            anteriores.put(rs.getInt(1), rs.getBoolean(2));
            float dosis = rs.getFloat(3);
            Float d = rs.wasNull() ? null : dosis;
            int cada = rs.getInt(6);
            Integer c = rs.wasNull() ? null : cada;
            posologiaAnterior.put(rs.getInt(1), posologia(d, rs.getString(4), rs.getString(5), c, rs.getString(7)));
        });
        Map<Integer, String> camaDelIngreso = new HashMap<>();
        estancias.values().forEach(e -> camaDelIngreso.putIfAbsent(e.idIngreso, e.espacio));

        int eventos = 0;
        for (Medicamento m : nuevos.values()) {
            Timestamp t = Timestamp.valueOf(ahora);
            Detalle x = m.detalle();
            Object[] valores = {m.idIndMedica, m.idIngreso, m.articulo, m.nombre, m.via, m.dosis, m.unidad, m.cada, m.cadaUnidad,
                    m.indicado == null ? null : Timestamp.valueOf(m.indicado), m.activo,
                    m.suspendido == null ? null : Timestamp.valueOf(m.suspendido), t,
                    x.nota(), x.folio(), x.externo(), x.dosis24h(), x.unidosis(), x.inicia() == null ? null : Timestamp.valueOf(x.inicia()),
                    x.infusion(), x.cantidadHora(), x.numeroHoras(), x.indico(), x.suspendio(), m.id};
            int cambiados = haero.update("""
                    UPDATE hosp_medicamento SET id_ind_medica = ?, id_ingreso = ?, clave = ?, medicamento = ?, via = ?, dosis = ?, unidad = ?,
                           cada = ?, cada_unidad = ?, indicado_en = ?, activo = ?, suspendido_en = ?, actualizado_en = ?,
                           nota = ?, folio = ?, externo = ?, dosis_24h = ?, unidosis = ?, inicia = ?, infusion = ?, cantidad_hora = ?,
                           numero_horas = ?, indico = ?, suspendio = ?
                    WHERE id = ?
                    """, valores);
            if (cambiados == 0) {
                haero.update("""
                        INSERT INTO hosp_medicamento (id_ind_medica, id_ingreso, clave, medicamento, via, dosis, unidad, cada, cada_unidad,
                                                      indicado_en, activo, suspendido_en, actualizado_en, nota, folio, externo, dosis_24h,
                                                      unidosis, inicia, infusion, cantidad_hora, numero_horas, indico, suspendio, id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, valores);
            }
            if (primeraVez || !internadosAntes.contains(m.idIngreso)) {
                continue;
            }
            Boolean antes = anteriores.get(m.id);
            String cama = camaDelIngreso.get(m.idIngreso);
            if (antes == null && m.activo) {
                eventos += evento(ahora, MEDICAMENTO_NUEVO, m.idIngreso, cama, central, "Se indicó " + m.descripcion(), x.indico(), m.articulo);
            } else if (Boolean.TRUE.equals(antes) && !m.activo) {
                eventos += evento(ahora, MEDICAMENTO_SUSPENDIDO, m.idIngreso, cama, central, "Se suspendió " + m.descripcion(),
                        x.suspendio() != null ? x.suspendio() : x.indico(), m.articulo);
            } else if (Boolean.TRUE.equals(antes) && m.activo) {
                String ahoraPosologia = posologia(m.dosis, m.unidad, m.via, m.cada, m.cadaUnidad);
                String antesPosologia = posologiaAnterior.get(m.id);
                if (antesPosologia != null && !antesPosologia.equals(ahoraPosologia)) {
                    eventos += evento(ahora, MEDICAMENTO_MODIFICADO, m.idIngreso, cama, central,
                            "Cambió " + (m.nombre != null ? m.nombre : m.articulo) + ": antes " + antesPosologia + ", ahora " + ahoraPosologia,
                            x.indico(), m.articulo);
                }
            }
        }
        return eventos;
    }

    /** Lo de pacientes egresados hace más de los días de retención se borra (datos clínicos). */
    private void borrarVencidos(LocalDateTime ahora) {
        Timestamp limite = Timestamp.valueOf(ahora.minusDays(diasRetencion));
        haero.update("""
                DELETE FROM hosp_medicamento WHERE id_ingreso NOT IN (SELECT id_ingreso FROM hosp_estancia WHERE activa = 1)
                  AND id_ingreso IN (SELECT id_ingreso FROM hosp_estancia GROUP BY id_ingreso HAVING MAX(egreso_detectado) < ?)
                """, limite);
        haero.update("DELETE FROM hosp_estancia WHERE activa = 0 AND egreso_detectado < ?", limite);
        haero.update("DELETE FROM hosp_evento WHERE fecha < ?", limite);
    }

    private int evento(LocalDateTime fecha, String tipo, int idIngreso, String espacio, Map<String, String> central, String detalle) {
        return evento(fecha, tipo, idIngreso, espacio, central, detalle, null, null);
    }

    private int evento(LocalDateTime fecha, String tipo, int idIngreso, String espacio, Map<String, String> central, String detalle,
                       String medico, String clave) {
        haero.update("INSERT INTO hosp_evento (fecha, tipo, id_ingreso, espacio, central, detalle, medico, clave) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Timestamp.valueOf(fecha), tipo, idIngreso, espacio, espacio == null ? null : central.get(espacio), recortar(detalle, 400),
                medico, clave);
        return 1;
    }

    /**
     * Usuario, nombre, cédula y especialidad de los médicos (agregar "IN (...)" al final).
     * vsa_RecursoHumano está vacía: el nombre sale de Usuario y la cédula y especialidad de Personal.
     * Muchos usuarios (sobre todo los que escriben la indicación) no tienen ligado su Personal; para
     * ellos se busca el Personal con el mismo nombre completo, prefiriendo el que tenga cédula.
     */
    public static final String SQL_MEDICOS = "SELECT u.Usuario, u.Nombre, "
            + "COALESCE(NULLIF(LTRIM(RTRIM(p.vsa_CedulaProfesional)), ''), n.vsa_CedulaProfesional), "
            + "COALESCE(NULLIF(LTRIM(RTRIM(p.vsa_Especialidad)), ''), n.vsa_Especialidad) "
            + "FROM Usuario u WITH (NOLOCK) "
            + "LEFT JOIN Personal p WITH (NOLOCK) ON p.Personal = u.Personal "
            + "OUTER APPLY (SELECT TOP 1 NULLIF(LTRIM(RTRIM(x.vsa_CedulaProfesional)), '') vsa_CedulaProfesional, "
            + "NULLIF(LTRIM(RTRIM(x.vsa_Especialidad)), '') vsa_Especialidad FROM Personal x WITH (NOLOCK) "
            + "WHERE LTRIM(RTRIM(ISNULL(x.Nombre, '') + ' ' + ISNULL(x.ApellidoPaterno, '') + ' ' + ISNULL(x.ApellidoMaterno, ''))) "
            + "= LTRIM(RTRIM(u.Nombre)) "
            + "ORDER BY CASE WHEN NULLIF(LTRIM(RTRIM(x.vsa_CedulaProfesional)), '') IS NULL THEN 1 ELSE 0 END) n "
            + "WHERE u.Usuario IN ";

    /** Nombre (Usuario), cédula y especialidad (Personal) de los médicos, por usuario de Intelisis. */
    private List<Object[]> leerMedicos(JdbcTemplate intelisis, Set<String> usuarios) {
        List<Object[]> medicos = new ArrayList<>();
        List<String> lista = new ArrayList<>(usuarios);
        for (int i = 0; i < lista.size(); i += 500) {
            List<String> parte = lista.subList(i, Math.min(lista.size(), i + 500));
            String marcadores = String.join(", ", java.util.Collections.nCopies(parte.size(), "?"));
            intelisis.query(SQL_MEDICOS + "(" + marcadores + ")", rs -> {
                medicos.add(new Object[]{texto(rs, 1), texto(rs, 2), texto(rs, 3), texto(rs, 4)});
            }, parte.toArray());
        }
        return medicos;
    }

    private void guardarMedicos(List<Object[]> medicos, LocalDateTime ahora) {
        Set<String> vistos = new HashSet<>();
        for (Object[] m : medicos) {
            if (m[0] == null || !vistos.add((String) m[0])) {
                continue;
            }
            Timestamp t = Timestamp.valueOf(ahora);
            if (haero.update("UPDATE hosp_medico SET nombre = ?, cedula = ?, especialidad = ?, actualizado_en = ? WHERE usuario = ?",
                    m[1], m[2], m[3], t, m[0]) == 0) {
                haero.update("INSERT INTO hosp_medico (usuario, nombre, cedula, especialidad, actualizado_en) VALUES (?, ?, ?, ?, ?)",
                        m[0], m[1], m[2], m[3], t);
            }
        }
    }

    /** "1 GRAMO INTRAVENOSA c/8 horas": para detectar cambios de dosis, vía o frecuencia. */
    public static String posologia(Float dosis, String unidad, String via, Integer cada, String cadaUnidad) {
        StringBuilder texto = new StringBuilder();
        if (dosis != null) {
            texto.append(dosis % 1 == 0 ? String.valueOf(dosis.intValue()) : String.valueOf(dosis));
            if (unidad != null) texto.append(' ').append(unidad);
        }
        if (via != null) texto.append(' ').append(via);
        if (cada != null && cada > 0) texto.append(" c/").append(cada).append(cadaUnidad != null ? " " + cadaUnidad : "");
        return texto.toString().strip();
    }

    private Long abrirBitacora(LocalDateTime inicio) {
        haero.update("INSERT INTO hosp_sincronizacion (inicio, estatus) VALUES (?, 'EN_CURSO')", Timestamp.valueOf(inicio));
        return haero.queryForObject("SELECT MAX(id) FROM hosp_sincronizacion", Long.class);
    }

    /**
     * Intelisis guarda el nombre como "010.000.1768.00 - VINCRISTINA ..., 1 MG - DISPONIBLE: 5" (a veces con
     * un prefijo como "G-" antes de la clave):
     * se quita la clave (ya va aparte) y la existencia que tenía al indicarse.
     */
    static String limpiarNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        // Claves como 010.000.0406.00, 010.000.FCB.01 o 010.000.0406.00U; "Disponible" en cualquier mayúscula.
        String limpio = nombre.replaceFirst("^(?:[A-Za-z]{1,3}-)?\\d{3}\\.\\d{3}\\.[0-9A-Za-z]{3,4}\\.\\d{2}[A-Za-z]?(-1)?\\s*-\\s*", "")
                .replaceFirst("(?i)\\s*-\\s*disponible:.*$", "")
                .strip();
        return limpio.isEmpty() ? nombre.strip() : limpio;
    }

    private static String texto(ResultSet rs, int columna) throws SQLException {
        String valor = rs.getString(columna);
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String recortar(String valor, int maximo) {
        return valor.length() > maximo ? valor.substring(0, maximo) : valor;
    }

    private record Estancia(int idArea, int idIngreso, String espacio, String servicio, LocalDateTime fechaIngreso,
                            String expediente, String paciente) {
    }

    /** Lo que la hoja "Indicaciones médicas activas" muestra de cada medicamento. */
    record Detalle(String nota, Integer folio, boolean externo, Integer dosis24h, boolean unidosis, LocalDateTime inicia,
                   boolean infusion, Float cantidadHora, Integer numeroHoras, String indico, String suspendio) {
        static final Detalle VACIO = new Detalle(null, null, false, null, false, null, false, null, null, null, null);
    }

    record Medicamento(int id, int idIndMedica, int idIngreso, String articulo, String nombre, String via, Float dosis,
                       String unidad, Integer cada, String cadaUnidad, LocalDateTime indicado, boolean activo,
                       LocalDateTime suspendido, Detalle detalle) {

        Medicamento(int id, int idIndMedica, int idIngreso, String articulo, String nombre, String via, Float dosis,
                    String unidad, Integer cada, String cadaUnidad, LocalDateTime indicado, boolean activo, LocalDateTime suspendido) {
            this(id, idIndMedica, idIngreso, articulo, nombre, via, dosis, unidad, cada, cadaUnidad, indicado, activo, suspendido, Detalle.VACIO);
        }

        /** "PARACETAMOL 1 g VO c/8 h" con lo que venga lleno. */
        String descripcion() {
            StringBuilder texto = new StringBuilder(nombre != null ? nombre : articulo != null ? articulo : "medicamento");
            if (dosis != null) {
                texto.append(' ').append(dosis % 1 == 0 ? String.valueOf(dosis.intValue()) : String.valueOf(dosis));
                if (unidad != null) {
                    texto.append(' ').append(unidad);
                }
            }
            if (via != null) {
                texto.append(' ').append(via);
            }
            if (cada != null && cada > 0) {
                texto.append(" c/").append(cada).append(cadaUnidad != null ? " " + cadaUnidad : "");
            }
            return texto.toString();
        }
    }
}
