package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.farmacia.dto.PacienteResponse;
import com.example.backend.farmacia.dto.PartidaRecetaIntelisis;
import com.example.backend.farmacia.dto.RecetaIntelisisResponse;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.RecetaRepository;
import com.example.backend.intelisis.config.ConexionIntelisis;
import com.example.backend.intelisis.service.NombrePaciente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU28: recetas firmadas del paciente en Intelisis (vsa_Receta y vsa_RecetaD), para que
 * Farmacia elija la receta y se llenen solos el folio y las claves.
 *
 * Validado con datos reales (2026-10-27): vsa_Receta.ExpKey es Cte.Cliente (el expediente),
 * cada receta trae dos números de 6 dígitos: ID_Receta (el que usa el envío a AAMATES) y
 * Folio; se busca por cualquiera de los dos. vsa_RecetaD.Articulo es la clave del catálogo.
 * Solo se leen folio, fecha, origen, médico, clave y cantidad: nada de diagnósticos ni indicaciones.
 * Igual que los pacientes, se consulta en el momento y no se copia a SICOMED.
 */
@Service
public class ServicioRecetasIntelisis {

    /**
     * vsa_RecetaD.Cantidad no trae unidad (Unidad y Factor vienen vacíos). Validado en Intelisis el 2026-11-26 con un
     * año de recetas: casi siempre son CAJAS (160 de 276 tabletas y demás se surtieron como cajas completas), pero hay
     * medicamentos que se recetan por pieza (ampolletas sueltas, por ejemplo). Se toma como cajas y el despachador lo
     * corrige. Se redondea hacia arriba y nunca queda en cero.
     * shortcut: una regla por medicamento (aprendida de lo que Farmacia surtió en Intelisis) evitaría corregir a mano.
     */
    static int piezasIndicadas(double cantidadReceta, Integer piezasPorCaja) {
        int cantidad = Math.max(1, (int) Math.ceil(cantidadReceta));
        return piezasPorCaja == null || piezasPorCaja <= 0 ? cantidad : cantidad * piezasPorCaja;
    }

    private static final Logger log = LoggerFactory.getLogger(ServicioRecetasIntelisis.class);
    private static final int MAXIMO_RECETAS = 20;

    private final ConexionIntelisis conexion;
    private final MedicamentoRepository medicamentoRepository;
    private final RecetaRepository recetaRepository;
    private final int dias;

    public ServicioRecetasIntelisis(ConexionIntelisis conexion,
                                    MedicamentoRepository medicamentoRepository,
                                    RecetaRepository recetaRepository,
                                    @Value("${intelisis.farmacia.dias-recetas:30}") int dias) {
        this.conexion = conexion;
        this.medicamentoRepository = medicamentoRepository;
        this.recetaRepository = recetaRepository;
        this.dias = dias;
    }

    /** Las recetas firmadas del paciente en los últimos días configurados, la más reciente primero. */
    public List<RecetaIntelisisResponse> delPaciente(String expediente) {
        String exp = ServicioInventarioFarmacia.texto(expediente);
        if (exp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el expediente del paciente");
        }
        return consultar("WHERE r.ExpKey = ? AND r.Estatus = 'FIRMADO' AND r.Fecha >= DATEADD(day, ?, GETDATE())", exp, -dias);
    }

    /** La receta con ese folio (Folio o ID_Receta), con su paciente. */
    public RecetaIntelisisResponse porFolio(String folio) {
        String limpio = ServicioInventarioFarmacia.texto(folio);
        if (limpio == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el folio de la receta");
        }
        // Primero por Folio; solo si no hay, por ID_Receta (un mismo número puede ser folio de una y ID de otra).
        List<RecetaIntelisisResponse> porFolio = consultar("WHERE r.Folio = ?", limpio);
        if (porFolio.isEmpty() && limpio.matches("\\d{1,9}")) {
            porFolio = consultar("WHERE r.ID_Receta = ?", Integer.parseInt(limpio));
        }
        return porFolio.stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una receta con el folio " + limpio + " en Intelisis"));
    }

    private List<RecetaIntelisisResponse> consultar(String where, Object... parametros) {
        JdbcTemplate intelisis = conexion.plantilla().orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No hay conexión con Intelisis para consultar las recetas. " + conexion.motivoNoDisponible()));
        try {
            Map<Integer, Object[]> recetas = new LinkedHashMap<>();
            // Médico: quien hizo la receta (siempre viene); el adscrito solo en algunas (unas 1 de cada 4).
            intelisis.query("SELECT TOP (" + MAXIMO_RECETAS + ") r.ID_Receta, r.Folio, r.Fecha, r.Origen, c.Cliente, " + NombrePaciente.SQL + ", c.CURP, "
                    + "COALESCE(um.Nombre, r.Usuario), CASE WHEN r.MedicoAdscrito <> r.Usuario THEN COALESCE(ua.Nombre, r.MedicoAdscrito) END, r.Estatus "
                    + "FROM vsa_Receta r WITH (NOLOCK) LEFT JOIN Cte c WITH (NOLOCK) ON c.Cliente = r.ExpKey "
                    + "LEFT JOIN Usuario um WITH (NOLOCK) ON um.Usuario = r.Usuario "
                    + "LEFT JOIN Usuario ua WITH (NOLOCK) ON ua.Usuario = r.MedicoAdscrito "
                    + where + " ORDER BY r.Fecha DESC", rs -> {
                Timestamp fecha = rs.getTimestamp(3);
                recetas.put(rs.getInt(1), new Object[]{limpio(rs.getString(2)), fecha == null ? null : fecha.toLocalDateTime(),
                        limpio(rs.getString(4)), new PacienteResponse(NombrePaciente.ordenar(limpio(rs.getString(6)), rs.getString(7)), limpio(rs.getString(5)), limpio(rs.getString(7))),
                        limpio(rs.getString(8)), limpio(rs.getString(9)), limpio(rs.getString(10))});
            }, parametros);
            if (recetas.isEmpty()) {
                return List.of();
            }

            Map<Integer, List<Object[]>> partidas = new HashMap<>();
            String marcadores = recetas.keySet().stream().map(id -> "?").collect(Collectors.joining(", "));
            intelisis.query("SELECT d.ID_Receta, d.Articulo, d.Farmaco, d.Cantidad FROM vsa_RecetaD d WITH (NOLOCK) "
                    + "WHERE d.ID_Receta IN (" + marcadores + ") ORDER BY d.ID_Receta, d.ID_RecetaD", rs -> {
                partidas.computeIfAbsent(rs.getInt(1), id -> new ArrayList<>())
                        .add(new Object[]{limpio(rs.getString(2)), limpio(rs.getString(3)), rs.getDouble(4)});
            }, recetas.keySet().toArray());

            Map<String, Medicamento> catalogo = medicamentoRepository.findAllById(partidas.values().stream().flatMap(List::stream)
                    .map(p -> claveBase((String) p[0])).filter(c -> c != null).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(Medicamento::getClave, Function.identity()));

            List<RecetaIntelisisResponse> respuesta = new ArrayList<>();
            recetas.forEach((id, r) -> {
                String folio = (String) r[0];
                String estadoEnHaero = folio == null ? null : recetaRepository.findByFolioIgnoreCase(folio).map(Receta::getEstado).orElse(null);
                List<PartidaRecetaIntelisis> claves = partidas.getOrDefault(id, List.of()).stream().map(p -> {
                    Medicamento medicamento = catalogo.get(claveBase((String) p[0]));
                    return medicamento != null
                            ? new PartidaRecetaIntelisis(medicamento.getClave(), medicamento.getNombreGenerico(), medicamento.getPiezasPorCaja(),
                                    piezasIndicadas((double) p[2], medicamento.getPiezasPorCaja()), true)
                            : new PartidaRecetaIntelisis(null, p[1] != null ? (String) p[1] : (String) p[0], null, piezasIndicadas((double) p[2], null), false);
                }).toList();
                respuesta.add(new RecetaIntelisisResponse(folio, String.valueOf(id), (LocalDateTime) r[1], (String) r[2],
                        (PacienteResponse) r[3], estadoEnHaero, claves, (String) r[4], (String) r[5], (String) r[6]));
            });
            return respuesta;
        } catch (DataAccessException e) {
            log.warn("No se pudieron leer las recetas de Intelisis: {}", e.getMostSpecificCause().getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Intelisis no respondió; intenta de nuevo en un momento");
        }
    }

    private static String claveBase(String articulo) {
        if (articulo == null) {
            return null;
        }
        return com.example.backend.intelisis.service.ServicioSincronizacionIntelisis.claveBase(
                com.example.backend.intelisis.service.ServicioSincronizacionIntelisis.limpiarArticulo(articulo.trim()));
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
