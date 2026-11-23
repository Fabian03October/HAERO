package com.example.backend.farmacia.service;

import com.example.backend.farmacia.dto.EntregaPacienteResponse;
import com.example.backend.intelisis.ConexionIntelisis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Entregas a pacientes ANTERIORES a HAERO, leídas en vivo de Intelisis (solo lectura).
 *
 * Validado en la red del hospital (2026-11-23): cada "Consumo Farmacia" del almacén 25301 es un
 * documento por paciente, con el nombre completo del paciente en Inv.Referencia y los lotes en
 * SerieLoteMov. No trae expediente: se busca en Cte por el nombre exacto y solo se pone si hay
 * un único paciente con ese nombre (≈62 % de los casos; con homónimos o el nombre escrito
 * distinto se muestra solo el nombre como quedó en la baja).
 *
 * Los pacientes no se copian a HAERO: se consulta al momento, como en Dispensación.
 */
@Service
public class ServicioBajasPacienteIntelisis {

    private static final Logger log = LoggerFactory.getLogger(ServicioBajasPacienteIntelisis.class);
    static final int MAXIMO = 2000;

    private final ConexionIntelisis conexion;
    private final List<String> movimientosConsumo;

    public ServicioBajasPacienteIntelisis(ConexionIntelisis conexion,
                                          @Value("${intelisis.farmacia.movimientos-consumo:Consumo Farmacia}") String movimientos) {
        this.conexion = conexion;
        this.movimientosConsumo = Arrays.stream(movimientos.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    /** Resultado de una consulta: las entregas y, si Intelisis no respondió, el motivo. */
    public record Resultado(List<EntregaPacienteResponse> entregas, String aviso) {
        static Resultado sinConexion(String motivo) {
            return new Resultado(List.of(), motivo);
        }
    }

    /** Pacientes a los que Farmacia dio de baja ese lote (con o sin el sufijo de Intelisis). */
    public Resultado delLote(String lote, String clave, LocalDateTime antesDe) {
        String where = "(m.SerieLote = CAST(? AS VARCHAR(50)) OR m.SerieLote LIKE CAST(? AS VARCHAR(60)))";
        Resultado r = consultar(where, antesDe, lote, conSufijo(lote));
        return clave == null ? r
                : new Resultado(r.entregas().stream().filter(e -> clave.equals(e.getClave())).toList(), r.aviso());
    }

    /** Lo que se le dio de baja a un paciente, por su nombre exacto (como está en Cte). */
    public Resultado delPacientePorNombreExacto(String nombre, LocalDateTime antesDe) {
        return consultar("i.Referencia = CAST(? AS VARCHAR(100))", antesDe, nombre);
    }

    /** Por expediente: se toma el nombre del paciente en Cte y se buscan sus bajas. */
    public Resultado delExpediente(String expediente, LocalDateTime antesDe) {
        return delPacienteSegun("c.Cliente = CAST(? AS VARCHAR(50))", expediente, antesDe);
    }

    public Resultado delCurp(String curp, LocalDateTime antesDe) {
        return delPacienteSegun("c.CURP = CAST(? AS VARCHAR(30))", curp.toUpperCase(Locale.ROOT), antesDe);
    }

    /** Por parte del nombre: cada palabra debe aparecer en el nombre que quedó en la baja (sin distinguir acentos). */
    public Resultado delPacientePorPalabras(String texto, LocalDateTime antesDe) {
        String[] palabras = texto.trim().toUpperCase(Locale.ROOT).split("\\s+");
        String where = Arrays.stream(palabras).map(p -> "i.Referencia COLLATE Latin1_General_CI_AI LIKE CAST(? AS VARCHAR(110)) COLLATE Latin1_General_CI_AI").collect(Collectors.joining(" AND "));
        Object[] parametros = Arrays.stream(palabras).map(p -> "%" + escapar(p) + "%").toArray();
        return consultar(where, antesDe, parametros);
    }

    private Resultado delPacienteSegun(String filtroCte, String valor, LocalDateTime antesDe) {
        JdbcTemplate intelisis = conexion.plantilla().orElse(null);
        if (intelisis == null) {
            return Resultado.sinConexion("Sin conexión con Intelisis: no se muestran las entregas anteriores a HAERO. " + conexion.motivoNoDisponible());
        }
        try {
            List<String> nombres = intelisis.queryForList("SELECT TOP (5) LTRIM(RTRIM(c.Nombre)) FROM Cte c WITH (NOLOCK) WHERE " + filtroCte, String.class, valor)
                    .stream().filter(n -> n != null && !n.isBlank()).distinct().toList();
            if (nombres.isEmpty()) {
                return new Resultado(List.of(), null);
            }
            String where = "i.Referencia IN (" + nombres.stream().map(n -> "CAST(? AS VARCHAR(100))").collect(Collectors.joining(", ")) + ")";
            return consultar(where, antesDe, nombres.toArray());
        } catch (DataAccessException e) {
            log.warn("No se pudo buscar al paciente en Intelisis: {}", e.getMostSpecificCause().getMessage());
            return Resultado.sinConexion("Intelisis no respondió: no se muestran las entregas anteriores a HAERO.");
        }
    }

    private Resultado consultar(String filtro, LocalDateTime antesDe, Object... parametros) {
        JdbcTemplate intelisis = conexion.plantilla().orElse(null);
        if (intelisis == null) {
            return Resultado.sinConexion("Sin conexión con Intelisis: no se muestran las entregas anteriores a HAERO. " + conexion.motivoNoDisponible());
        }
        List<Object> args = new ArrayList<>(movimientosConsumo);
        args.addAll(Arrays.asList(parametros));
        String corte = "";
        if (antesDe != null) {
            corte = " AND i.FechaEmision < ?";
            args.add(Timestamp.valueOf(antesDe));
        }
        String sql = "SELECT TOP (" + MAXIMO + ") i.FechaEmision, i.Mov, i.MovID, LTRIM(RTRIM(i.Referencia)), m.Articulo, m.SerieLote, "
                + "ABS(m.Cantidad), i.Usuario "
                + "FROM SerieLoteMov m WITH (NOLOCK) JOIN Inv i WITH (NOLOCK) ON i.ID = m.ID "
                + "WHERE m.Modulo = 'INV' AND i.Estatus <> 'CANCELADO' "
                + "AND i.Mov IN (" + movimientosConsumo.stream().map(x -> "CAST(? AS VARCHAR(30))").collect(Collectors.joining(", ")) + ") "
                + "AND " + filtro + corte + " ORDER BY i.FechaEmision DESC";
        try {
            List<Fila> filas = intelisis.query(sql, (rs, n) -> {
                Timestamp fecha = rs.getTimestamp(1);
                String movId = limpio(rs.getString(3));
                return new Fila(fecha == null ? null : fecha.toLocalDateTime(),
                        limpio(rs.getString(2)) + (movId != null ? " " + movId : ""),
                        limpio(rs.getString(4)), ServicioTrazabilidad.claveBase(rs.getString(5)),
                        ServicioTrazabilidad.loteBase(rs.getString(6)),
                        rs.getBigDecimal(7) == null ? 0 : rs.getBigDecimal(7).intValue(), limpio(rs.getString(8)));
            }, args.toArray());
            Map<String, String> expedientes = expedientesPorNombre(intelisis, filas.stream().map(Fila::paciente).filter(Objects::nonNull).distinct().toList());
            List<EntregaPacienteResponse> entregas = filas.stream().map(f -> new EntregaPacienteResponse(f.fecha(), f.folio(), f.paciente(),
                    f.paciente() == null ? null : expedientes.get(f.paciente()), f.clave(), f.lote(), f.piezas(), f.registro(), "Intelisis")).toList();
            return new Resultado(entregas, entregas.size() >= MAXIMO
                    ? "Intelisis tiene más de " + MAXIMO + " entregas para esta búsqueda; se muestran las más recientes." : null);
        } catch (DataAccessException e) {
            log.warn("No se pudieron leer las bajas por paciente de Intelisis: {}", e.getMostSpecificCause().getMessage());
            return Resultado.sinConexion("Intelisis no respondió: no se muestran las entregas anteriores a HAERO.");
        }
    }

    private record Fila(LocalDateTime fecha, String folio, String paciente, String clave, String lote, int piezas, String registro) {
    }

    /**
     * Expediente de cada nombre, en consultas de 1,000 nombres (SQL Server admite hasta 2,100
     * parámetros). Un paciente registrado más de una vez en Cte (pasa) muestra sus expedientes
     * como "X / Y"; con más de 3 es un nombre común y se deja vacío.
     */
    private static Map<String, String> expedientesPorNombre(JdbcTemplate intelisis, List<String> nombres) {
        Map<String, java.util.Set<String>> encontrados = new HashMap<>();
        for (int i = 0; i < nombres.size(); i += 1000) {
            List<String> grupo = nombres.subList(i, Math.min(i + 1000, nombres.size()));
            String marcadores = grupo.stream().map(n -> "CAST(? AS VARCHAR(100))").collect(Collectors.joining(", "));
            intelisis.query("SELECT LTRIM(RTRIM(c.Nombre)), c.Cliente FROM Cte c WITH (NOLOCK) WHERE c.Nombre IN (" + marcadores + ")", rs -> {
                String expediente = limpio(rs.getString(2));
                if (expediente != null) {
                    encontrados.computeIfAbsent(rs.getString(1), k -> new java.util.TreeSet<>()).add(expediente);
                }
            }, grupo.toArray());
        }
        Map<String, String> expedientes = new HashMap<>();
        encontrados.forEach((nombre, lista) -> {
            if (lista.size() <= 3) {
                expedientes.put(nombre, String.join(" / ", lista));
            }
        });
        return expedientes;
    }

    private static String conSufijo(String lote) {
        return escapar(lote) + "[ /]%";
    }

    private static String escapar(String texto) {
        return texto.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
