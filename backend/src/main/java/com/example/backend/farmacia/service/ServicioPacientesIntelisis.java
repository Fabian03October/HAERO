package com.example.backend.farmacia.service;

import com.example.backend.farmacia.dto.PacienteResponse;
import com.example.backend.intelisis.ConexionIntelisis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * HU28 CA1: busca al paciente en Intelisis, en solo lectura, por expediente, nombre o CURP.
 *
 * A diferencia del catálogo, los pacientes NO se copian a HAERO (son datos personales de
 * salud): se consulta Intelisis en el momento, con pocas filas, y HAERO solo guarda los
 * datos del paciente al que se le entrega una receta.
 *
 * Por validar en la red del hospital: el paciente está en Cte (Cte.Cliente, el mismo que
 * usan vsa_IngHosp y vsa_Receta) y el número de expediente es Cte.Cliente.
 */
@Service
public class ServicioPacientesIntelisis {

    private static final Logger log = LoggerFactory.getLogger(ServicioPacientesIntelisis.class);
    private static final int MAXIMO = 20;
    private static final int MINIMO_NOMBRE = 3;
    // Formato oficial de la CURP (18 caracteres), igual que en la pantalla.
    private static final Pattern PATRON_CURP = Pattern.compile("^[A-Z][AEIOUX][A-Z]{2}\\d{6}[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\\d$");
    private static final String SELECT = "SELECT TOP (" + MAXIMO + ") c.Cliente, c.Nombre, c.CURP FROM Cte c WITH (NOLOCK) ";

    private final ConexionIntelisis conexion;

    public ServicioPacientesIntelisis(ConexionIntelisis conexion) {
        this.conexion = conexion;
    }

    public List<PacienteResponse> buscar(String expediente, String nombre, String curp) {
        String exp = ServicioInventarioFarmacia.texto(expediente);
        String nom = ServicioInventarioFarmacia.texto(nombre);
        String cur = ServicioInventarioFarmacia.texto(curp);
        if (exp == null && nom == null && cur == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el expediente, el nombre o la CURP del paciente");
        }
        if (cur != null) {
            cur = cur.toUpperCase(Locale.ROOT);
            if (!PATRON_CURP.matcher(cur).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La CURP no tiene el formato oficial de 18 caracteres");
            }
        }
        if (exp == null && cur == null && nom.length() < MINIMO_NOMBRE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe al menos " + MINIMO_NOMBRE + " letras del nombre");
        }

        JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No hay conexión con Intelisis para buscar al paciente. " + conexion.motivoNoDisponible()));
        try {
            if (exp != null) {
                return consultar(intelisis, SELECT + "WHERE c.Cliente = ? ORDER BY c.Nombre", exp);
            }
            if (cur != null) {
                return consultar(intelisis, SELECT + "WHERE c.CURP = ? ORDER BY c.Nombre", cur);
            }
            // Cada palabra del nombre debe aparecer, en cualquier orden (como se escribe en la receta).
            String[] palabras = nom.toUpperCase(Locale.ROOT).split("\\s+");
            StringBuilder where = new StringBuilder("WHERE 1 = 1");
            for (int i = 0; i < palabras.length; i++) {
                where.append(" AND c.Nombre LIKE ?");
                palabras[i] = "%" + palabras[i].replace("[", "[[]").replace("%", "[%]").replace("_", "[_]") + "%";
            }
            return consultar(intelisis, SELECT + where + " ORDER BY c.Nombre", (Object[]) palabras);
        } catch (DataAccessException e) {
            log.warn("No se pudo buscar el paciente en Intelisis: {}", e.getMostSpecificCause().getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Intelisis no respondió; intenta de nuevo en un momento");
        }
    }

    private static List<PacienteResponse> consultar(JdbcTemplate intelisis, String sql, Object... parametros) {
        return intelisis.query(sql, (rs, n) -> new PacienteResponse(
                limpio(rs.getString(2)), limpio(rs.getString(1)), limpio(rs.getString(3))), parametros);
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
