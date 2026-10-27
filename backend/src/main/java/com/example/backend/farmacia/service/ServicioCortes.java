package com.example.backend.farmacia.service;

import com.example.backend.entity.Usuario;
import com.example.backend.farmacia.dto.CorteDiaResponse;
import com.example.backend.farmacia.dto.RenglonBajaResponse;
import com.example.backend.farmacia.dto.RenglonResumenResponse;
import com.example.backend.farmacia.dto.RenglonVerificacionResponse;
import com.example.backend.farmacia.dto.VerificacionResponse;
import com.example.backend.farmacia.entity.BajaFarmacia;
import com.example.backend.farmacia.entity.CorteFarmacia;
import com.example.backend.farmacia.entity.Devolucion;
import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.PartidaDevolucion;
import com.example.backend.farmacia.entity.PartidaEntrega;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.BajaFarmaciaRepository;
import com.example.backend.farmacia.repository.CorteFarmaciaRepository;
import com.example.backend.farmacia.repository.DevolucionRepository;
import com.example.backend.farmacia.repository.EntregaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * HU31: resumen del día para dar una sola baja por volumen en Intelisis ("Consumo Farmacia"),
 * y HU32: verificación contra lo que se capturó en Intelisis.
 *
 * HAERO no escribe en Intelisis: la baja la captura una persona con este resumen y la marca
 * aquí. La verificación lee el espejo que llena la sincronización (intelisis_mov_farmacia_lote).
 */
@Service
public class ServicioCortes {

    // En Intelisis la clave de Farmacia (en piezas) termina en "-1".
    static final String SUFIJO_FARMACIA = "-1";

    private final EntregaRepository entregaRepository;
    private final DevolucionRepository devolucionRepository;
    private final BajaFarmaciaRepository bajaRepository;
    private final CorteFarmaciaRepository corteRepository;
    private final ServicioInventarioFarmacia inventario;
    private final JdbcTemplate haero;
    private final List<String> movimientosConsumo;

    public ServicioCortes(EntregaRepository entregaRepository,
                          DevolucionRepository devolucionRepository,
                          BajaFarmaciaRepository bajaRepository,
                          CorteFarmaciaRepository corteRepository,
                          ServicioInventarioFarmacia inventario,
                          JdbcTemplate haero,
                          @Value("${intelisis.farmacia.movimientos-consumo:Consumo Farmacia}") String movimientosConsumo) {
        this.entregaRepository = entregaRepository;
        this.devolucionRepository = devolucionRepository;
        this.bajaRepository = bajaRepository;
        this.corteRepository = corteRepository;
        this.inventario = inventario;
        this.haero = haero;
        this.movimientosConsumo = Arrays.stream(movimientosConsumo.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
    }

    // ---------- HU31: resumen del día ----------

    public CorteDiaResponse resumen(LocalDate dia) {
        LocalDateTime desde = dia.atStartOfDay();
        LocalDateTime hasta = dia.plusDays(1).atStartOfDay();
        List<Entrega> entregas = entregaRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta);
        List<Devolucion> devoluciones = devolucionRepository.findByDiaResumen(dia);
        List<BajaFarmacia> bajas = bajaRepository.findByFechaBetweenOrderByFechaAsc(desde, hasta);

        // CA1: consumo de pacientes por clave y lote; CA2: ya resta las devoluciones del día.
        Map<String, Acumulado> consumo = new TreeMap<>();
        Set<String> pacientes = new HashSet<>();
        for (Entrega entrega : entregas) {
            String paciente = paciente(entrega.getReceta());
            pacientes.add(paciente);
            for (PartidaEntrega partida : entrega.getPartidas()) {
                Acumulado a = consumo.computeIfAbsent(llave(partida.getLote()), k -> new Acumulado(partida.getLote()));
                a.piezas += partida.getPiezas();
                a.pacientes.add(paciente);
            }
        }
        Map<String, Acumulado> devueltas = new TreeMap<>();
        for (Devolucion devolucion : devoluciones) {
            boolean resta = Devolucion.RESTA.equals(devolucion.getEnResumen());
            for (PartidaDevolucion partida : devolucion.getPartidas()) {
                LoteFarmacia lote = partida.getPartidaEntrega().getLote();
                Map<String, Acumulado> destino = resta ? consumo : devueltas;
                Acumulado a = destino.computeIfAbsent(llave(lote), k -> new Acumulado(lote));
                a.piezas += resta ? -partida.getPiezas() : partida.getPiezas();
                if (!resta) {
                    a.pacientes.add(paciente(devolucion.getEntrega().getReceta()));
                }
            }
        }
        List<RenglonResumenResponse> renglonesConsumo = consumo.values().stream().filter(a -> a.piezas > 0).map(Acumulado::renglon).toList();
        List<RenglonResumenResponse> renglonesDevolucion = devueltas.values().stream().map(Acumulado::renglon).toList();

        // Las bajas van aparte: no son consumo de pacientes.
        Map<String, int[]> porMotivo = new TreeMap<>();
        Map<String, String[]> datosBaja = new LinkedHashMap<>();
        for (BajaFarmacia baja : bajas) {
            String llave = llave(baja.getLote()) + "|" + baja.getMotivo();
            porMotivo.computeIfAbsent(llave, k -> new int[1])[0] += baja.getPiezas();
            datosBaja.putIfAbsent(llave, new String[]{claveIntelisis(baja.getLote()), baja.getLote().getNumeroLote(), baja.getMotivo()});
        }
        List<RenglonBajaResponse> renglonesBaja = new ArrayList<>();
        porMotivo.forEach((llave, piezas) -> {
            String[] datos = datosBaja.get(llave);
            renglonesBaja.add(new RenglonBajaResponse(datos[0], datos[1], piezas[0], datos[2]));
        });

        CorteFarmacia corte = corteRepository.findById(dia).orElse(null);
        int cambios = 0;
        if (corte != null) {
            LocalDateTime marcado = corte.getMarcadoEn();
            cambios = (int) (entregas.stream().filter(e -> e.getFecha().isAfter(marcado)).count()
                    + devoluciones.stream().filter(d -> d.getFecha().isAfter(marcado)).count()
                    + bajas.stream().filter(b -> b.getFecha().isAfter(marcado)).count());
        }
        Set<String> claves = new HashSet<>();
        renglonesConsumo.forEach(r -> claves.add(r.getClaveIntelisis()));
        return new CorteDiaResponse(dia, corte == null ? CorteFarmacia.PENDIENTE : corte.getEstado(),
                corte == null ? null : corte.getFolioIntelisis(), corte == null ? null : corte.getMarcadoEn(),
                corte == null ? null : corte.getMarcadoPor().getNombreCompleto(), cambios,
                renglonesConsumo, renglonesDevolucion, renglonesBaja,
                claves.size(), renglonesConsumo.stream().mapToInt(RenglonResumenResponse::getPiezas).sum(), pacientes.size(),
                diasPendientes());
    }

    /**
     * CA5: días con entregas, devoluciones o bajas que falta dar de baja: los anteriores a hoy
     * sin marcar y los marcados que cambiaron después de marcarse.
     */
    public List<LocalDate> diasPendientes() {
        String sql = """
                SELECT x.dia
                FROM (
                    SELECT CAST(fecha AS date) AS dia, MAX(fecha) AS ultima FROM farmacia_entrega GROUP BY CAST(fecha AS date)
                    UNION ALL
                    SELECT dia_resumen, MAX(fecha) FROM farmacia_devolucion GROUP BY dia_resumen
                    UNION ALL
                    SELECT CAST(fecha AS date), MAX(fecha) FROM farmacia_baja GROUP BY CAST(fecha AS date)
                ) x
                LEFT JOIN farmacia_corte c ON c.dia = x.dia
                GROUP BY x.dia, c.marcado_en
                HAVING (c.marcado_en IS NULL AND x.dia < ?) OR MAX(x.ultima) > c.marcado_en
                ORDER BY x.dia
                """;
        // La fecha sale de la aplicación: el SQL Server puede correr en otra zona horaria (UTC).
        return haero.query(sql, (rs, n) -> rs.getDate(1).toLocalDate(), java.sql.Date.valueOf(LocalDate.now()));
    }

    /** CA4: el responsable marca el día como dado de baja en Intelisis (con el folio, opcional). */
    @Transactional
    public CorteDiaResponse marcarDadoDeBaja(LocalDate dia, String folioIntelisis, String nombreUsuario) {
        if (dia.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede dar de baja un día que aún no llega");
        }
        CorteDiaResponse resumen = resumen(dia);
        if (resumen.getConsumo().isEmpty() && resumen.getDevoluciones().isEmpty() && resumen.getBajas().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese día no tiene entregas, devoluciones ni bajas que dar de baja");
        }
        Usuario usuario = inventario.usuario(nombreUsuario);
        CorteFarmacia corte = corteRepository.findById(dia).orElseGet(CorteFarmacia::new);
        corte.setDia(dia);
        corte.setEstado(CorteFarmacia.DADO_DE_BAJA);
        corte.setFolioIntelisis(ServicioInventarioFarmacia.texto(folioIntelisis));
        corte.setMarcadoEn(LocalDateTime.now());
        corte.setMarcadoPor(usuario);
        corte.setVerificadoEn(null);
        // Se escribe ya: el resumen que se regresa lee los días pendientes con SQL directo.
        corteRepository.saveAndFlush(corte);
        return resumen(dia);
    }

    // ---------- HU32: verificación ----------

    @Transactional
    public VerificacionResponse verificar(LocalDate dia) {
        CorteFarmacia corte = corteRepository.findById(dia)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Primero marca el día como dado de baja en Intelisis"));
        CorteDiaResponse resumen = resumen(dia);

        // Lo que HAERO dice que salió ese día: consumo menos las "Devolucion Farmacia" del día.
        Map<String, int[]> haeroPorLote = new TreeMap<>();
        Map<String, String[]> nombres = new LinkedHashMap<>();
        resumen.getConsumo().forEach(r -> sumar(haeroPorLote, nombres, r.getClaveIntelisis(), r.getLote(), r.getPiezas()));
        resumen.getDevoluciones().forEach(r -> sumar(haeroPorLote, nombres, r.getClaveIntelisis(), r.getLote(), -r.getPiezas()));

        // CA1: lo dado de baja en Intelisis, del espejo de la sincronización.
        Map<String, int[]> intelisisPorLote = new TreeMap<>();
        String sql = """
                SELECT articulo, serie_lote, mov, SUM(cantidad) AS cantidad
                FROM intelisis_mov_farmacia_lote
                WHERE fecha_emision = ? AND articulo = clave_base + ?
                GROUP BY articulo, serie_lote, mov
                """;
        haero.query(sql, rs -> {
            String articulo = rs.getString(1).trim();
            String lote = rs.getString(2).trim();
            String mov = rs.getString(3).trim();
            int cantidad = rs.getBigDecimal(4).abs().intValue();
            boolean consumo = movimientosConsumo.stream().anyMatch(m -> m.equalsIgnoreCase(mov));
            sumar(intelisisPorLote, nombres, articulo, lote, consumo ? cantidad : -cantidad);
        }, java.sql.Date.valueOf(dia), SUFIJO_FARMACIA);

        Set<String> llaves = new java.util.TreeSet<>(haeroPorLote.keySet());
        llaves.addAll(intelisisPorLote.keySet());
        List<RenglonVerificacionResponse> renglones = new ArrayList<>();
        for (String llave : llaves) {
            int enHaero = haeroPorLote.getOrDefault(llave, new int[1])[0];
            int enIntelisis = intelisisPorLote.getOrDefault(llave, new int[1])[0];
            int diferencia = enHaero - enIntelisis;
            String resultado = diferencia == 0 ? "CUADRA" : diferencia > 0 ? "FALTA" : "DE_MAS";
            String[] datos = nombres.get(llave);
            renglones.add(new RenglonVerificacionResponse(datos[0], datos[1], enHaero, enIntelisis, diferencia, resultado));
        }

        // CA3: si todo cuadra, el día queda verificado.
        boolean cuadra = !renglones.isEmpty() && renglones.stream().allMatch(r -> "CUADRA".equals(r.getResultado()))
                && resumen.getCambiosDespuesDeLaBaja() == 0;
        corte.setEstado(cuadra ? CorteFarmacia.VERIFICADO : CorteFarmacia.DADO_DE_BAJA);
        corte.setVerificadoEn(cuadra ? LocalDateTime.now() : null);
        corteRepository.save(corte);
        return new VerificacionResponse(dia, corte.getEstado(), ultimaSincronizacion(), renglones);
    }

    /** El resumen en CSV (se abre en Excel) para capturar la baja en Intelisis. */
    public String csv(LocalDate dia) {
        CorteDiaResponse resumen = resumen(dia);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Resumen de Farmacia para Intelisis,").append(dia).append('\n');
        csv.append("Tipo,Clave Intelisis,Medicamento,Lote,Piezas,Pacientes\n");
        resumen.getConsumo().forEach(r -> csv.append(fila("Consumo Farmacia", r)));
        resumen.getDevoluciones().forEach(r -> csv.append(fila("Devolucion Farmacia", r)));
        csv.append('\n').append("Bajas por caducidad o daño (no son consumo)\n");
        csv.append("Clave Intelisis,Lote,Piezas,Motivo\n");
        resumen.getBajas().forEach(b -> csv.append(celda(b.getClaveIntelisis())).append(',').append(celda(b.getLote())).append(',')
                .append(b.getPiezas()).append(',').append(celda(b.getMotivo())).append('\n'));
        return csv.toString();
    }

    private LocalDateTime ultimaSincronizacion() {
        List<Timestamp> fin = haero.queryForList(
                "SELECT TOP 1 fin FROM intelisis_sincronizacion WHERE estatus = 'OK' ORDER BY id DESC", Timestamp.class);
        return fin.isEmpty() || fin.get(0) == null ? null : fin.get(0).toLocalDateTime();
    }

    private static void sumar(Map<String, int[]> mapa, Map<String, String[]> nombres, String clave, String lote, int piezas) {
        String llave = clave.toUpperCase(Locale.ROOT) + "|" + lote.toUpperCase(Locale.ROOT);
        mapa.computeIfAbsent(llave, k -> new int[1])[0] += piezas;
        nombres.putIfAbsent(llave, new String[]{clave, lote});
    }

    private static String fila(String tipo, RenglonResumenResponse r) {
        return celda(tipo) + "," + celda(r.getClaveIntelisis()) + "," + celda(r.getMedicamento()) + "," + celda(r.getLote())
                + "," + r.getPiezas() + "," + r.getPacientes() + "\n";
    }

    private static String celda(String valor) {
        String texto = valor == null ? "" : valor;
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }

    static String claveIntelisis(LoteFarmacia lote) {
        return lote.getMedicamento().getClave() + SUFIJO_FARMACIA;
    }

    private static String llave(LoteFarmacia lote) {
        return lote.getMedicamento().getClave() + "|" + lote.getNumeroLote().toUpperCase(Locale.ROOT);
    }

    private static String paciente(Receta receta) {
        return receta.getPacienteExpediente() != null ? "E:" + receta.getPacienteExpediente() : "N:" + receta.getPacienteNombre();
    }

    private static final class Acumulado {
        private final LoteFarmacia lote;
        private int piezas;
        private final Set<String> pacientes = new HashSet<>();

        private Acumulado(LoteFarmacia lote) {
            this.lote = lote;
        }

        private RenglonResumenResponse renglon() {
            return new RenglonResumenResponse(claveIntelisis(lote), lote.getMedicamento().getClave(),
                    lote.getMedicamento().getNombreGenerico(), lote.getNumeroLote(), piezas,
                    lote.getMedicamento().getPiezasPorCaja(), pacientes.size());
        }
    }
}
