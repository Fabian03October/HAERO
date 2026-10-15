package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.AlertaResponse;
import com.example.backend.almacen.dto.ConsumoMesResponse;
import com.example.backend.almacen.dto.RotacionResponse;
import com.example.backend.almacen.entity.Alerta;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.AlertaRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HU16 (consumo promedio mensual, CPM) y HU17 (alertas de stock minimo y
 * maximo), tal como las agrupa la carta CRC de ServicioRotacion en el
 * documento de diseno. El CPM nunca se guarda: se calcula al vuelo con las
 * salidas reales (tipo SALIDA, despachos a Farmacia - HU14) de los ultimos 6
 * meses cerrados. Mientras una clave no tenga salidas propias en la ventana,
 * se usa como respaldo el consumo promedio de la hoja de carga inicial
 * (HU21), igual que ya lo resolvia la version de Angular que reemplaza este
 * servicio.
 */
@Service
public class ServicioRotacion {

    private static final int MESES_VENTANA_CPM = 6;
    private static final int MESES_STOCK_MINIMO = 3;
    private static final int MESES_STOCK_MAXIMO = 6;
    // Mismo umbral que el frontend usa para ofrecer el canje (HU18): si a un
    // lote vigente le quedan menos de 9 meses, ademas de aparecer en la
    // pantalla de canje, ahora dispara una alerta proactiva (HU15/HU19 ajuste).
    private static final int MESES_ALERTA_CADUCIDAD = 9;
    private static final String TIPO_SALIDA_FARMACIA = "SALIDA";
    private static final String ESTATUS_LOTE_DISPONIBLE = "DISPONIBLE";
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MedicamentoRepository medicamentoRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final LoteRepository loteRepository;
    private final AlertaRepository alertaRepository;

    public ServicioRotacion(MedicamentoRepository medicamentoRepository,
                             ExistenciaRepository existenciaRepository,
                             MovimientoRepository movimientoRepository,
                             LoteRepository loteRepository,
                             AlertaRepository alertaRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.loteRepository = loteRepository;
        this.alertaRepository = alertaRepository;
    }

    // ---------- HU16: CPM ----------

    public List<RotacionResponse> listar() {
        Contexto contexto = contexto();
        return medicamentoRepository.findAll().stream()
                .map(m -> calcular(m, contexto))
                .toList();
    }

    public RotacionResponse obtener(String clave) {
        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        return calcular(medicamento, contexto());
    }

    /**
     * Datos que necesita el calculo de TODAS las claves, leidos una sola vez:
     * las salidas de la ventana agrupadas por clave y mes, y la existencia
     * vigente por clave. Antes se releian completos por cada clave, lo que con
     * el catalogo de Intelisis (cientos de claves) volvia lenta la pantalla.
     */
    private record Contexto(List<YearMonth> ventana, int mesesHistorial,
                            Map<String, Map<YearMonth, Integer>> salidasPorClaveYMes,
                            Map<String, Integer> existenciaVigentePorClave,
                            Map<Long, Integer> existenciaPorLote) {
    }

    private Contexto contexto() {
        LocalDate hoy = LocalDate.now();
        List<YearMonth> ventana = ventana(hoy);
        LocalDateTime desde = ventana.get(0).atDay(1).atStartOfDay();
        LocalDateTime hasta = ventana.get(ventana.size() - 1).atEndOfMonth().atTime(23, 59, 59, 999_000_000);

        Map<String, Map<YearMonth, Integer>> salidas = movimientoRepository.findByFechaBetweenOrderByFechaDesc(desde, hasta).stream()
                .filter(m -> TIPO_SALIDA_FARMACIA.equals(m.getTipo()))
                .collect(Collectors.groupingBy(m -> m.getExistencia().getLote().getMedicamento().getClave(),
                        Collectors.groupingBy(m -> YearMonth.from(m.getFecha()), Collectors.summingInt(Movimiento::getCantidadCajas))));

        List<Existencia> existencias = existenciaRepository.findAllConLote();
        // Sin contar los lotes ya vencidos (aunque sigan "DISPONIBLE").
        Map<String, Integer> existenciaVigente = existencias.stream()
                .filter(e -> !e.getLote().getCaducidad().isBefore(hoy))
                .collect(Collectors.groupingBy(e -> e.getLote().getMedicamento().getClave(), Collectors.summingInt(Existencia::getCantidadCajas)));
        Map<Long, Integer> existenciaPorLote = existencias.stream()
                .filter(e -> e.getLote().getId() != null)
                .collect(Collectors.groupingBy(e -> e.getLote().getId(), Collectors.summingInt(Existencia::getCantidadCajas)));

        return new Contexto(ventana, mesesHistorial(ventana), salidas, existenciaVigente, existenciaPorLote);
    }

    /** Los 6 meses calendario ya cerrados (no incluye el mes en curso), del mas antiguo al mas reciente. */
    private List<YearMonth> ventana(LocalDate hoy) {
        YearMonth mesActual = YearMonth.from(hoy);
        List<YearMonth> meses = new ArrayList<>();
        for (int i = MESES_VENTANA_CPM; i >= 1; i--) {
            meses.add(mesActual.minusMonths(i));
        }
        return meses;
    }

    /**
     * Cuantos meses de la ventana ya tienen datos reales en el sistema (desde
     * la primera salida jamas registrada). Evita dividir entre 6 cuando el
     * sistema lleva menos tiempo operando que la ventana completa.
     */
    private int mesesHistorial(List<YearMonth> ventana) {
        YearMonth primerMes = movimientoRepository.findFirstByTipoOrderByFechaAsc(TIPO_SALIDA_FARMACIA)
                .map(m -> YearMonth.from(m.getFecha()))
                .orElse(null);

        if (primerMes == null) {
            return 0;
        }

        return (int) ventana.stream().filter(mes -> !mes.isBefore(primerMes)).count();
    }

    private RotacionResponse calcular(Medicamento medicamento, Contexto contexto) {
        String clave = medicamento.getClave();
        int mesesHistorial = contexto.mesesHistorial();
        Map<YearMonth, Integer> cajasPorMes = contexto.salidasPorClaveYMes().getOrDefault(clave, Map.of());

        List<ConsumoMesResponse> meses = contexto.ventana().stream()
                .map(mes -> new ConsumoMesResponse(mes.format(FORMATO_MES), cajasPorMes.getOrDefault(mes, 0)))
                .toList();

        int totalEnVentana = meses.stream().mapToInt(ConsumoMesResponse::getCajas).sum();

        boolean usarHoja = totalEnVentana == 0 && medicamento.getConsumoPromedioHoja() != null && medicamento.getConsumoPromedioHoja() > 0;
        double cpm;
        String origenCpm;
        if (usarHoja) {
            cpm = medicamento.getConsumoPromedioHoja();
            origenCpm = "HOJA";
        } else if (mesesHistorial > 0) {
            cpm = Math.round((totalEnVentana / (double) mesesHistorial) * 10) / 10.0;
            origenCpm = "SISTEMA";
        } else {
            cpm = 0;
            origenCpm = "SISTEMA";
        }

        int existenciaActual = contexto.existenciaVigentePorClave().getOrDefault(clave, 0);
        double stockMinimo = Math.ceil(cpm * MESES_STOCK_MINIMO);
        double stockMaximo = Math.ceil(cpm * MESES_STOCK_MAXIMO);

        String estado;
        if (cpm <= 0) {
            estado = "SIN_CONSUMO";
        } else if (existenciaActual <= stockMinimo) {
            estado = "DESABASTO";
        } else if (existenciaActual >= stockMaximo) {
            estado = "SOBREABASTO";
        } else {
            estado = "NORMAL";
        }

        return new RotacionResponse(clave, medicamento.getNombreGenerico(), cpm, origenCpm, mesesHistorial,
                existenciaActual, stockMinimo, stockMaximo, estado, meses);
    }

    // ---------- HU17: alertas de stock minimo y maximo ----------

    public List<AlertaResponse> listarAlertas(boolean activas) {
        evaluarAlertas(); // mantiene el estado al dia en cada consulta; el @Scheduled es solo el respaldo

        List<Alerta> alertas = activas
                ? alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()
                : alertaRepository.findByFechaFinIsNotNullOrderByFechaFinDesc();

        return alertas.stream().map(this::aAlertaResponse).toList();
    }

    private AlertaResponse aAlertaResponse(Alerta alerta) {
        return new AlertaResponse(alerta.getId(), alerta.getMedicamento().getClave(), alerta.getMedicamento().getNombreGenerico(),
                alerta.getTipo(), alerta.getFechaInicio(), alerta.getFechaFin(), alerta.getExistenciaAlInicio(), alerta.getLimiteAlInicio(),
                alerta.getLote() != null ? alerta.getLote().getNumeroLote() : null,
                alerta.getLote() != null ? alerta.getLote().getCaducidad() : null);
    }

    /**
     * Recalcula el estado de cada clave y abre o cierra las alertas de stock
     * segun corresponda (HU17 CA2 a CA4). Se ejecuta cada hora para que las
     * alertas esten disponibles sin que nadie este conectado cuando se
     * generan, y tambien puede llamarse directo (por ejemplo en pruebas).
     */
    @Scheduled(fixedDelayString = "PT1H")
    public void evaluarAlertas() {
        Contexto contexto = contexto();

        // Todas las alertas abiertas en una sola consulta, de la mas antigua a la mas reciente.
        List<Alerta> abiertas = new ArrayList<>(alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc());
        Collections.reverse(abiertas);
        Map<String, List<Alerta>> abiertasDeStock = abiertas.stream()
                .filter(a -> a.getLote() == null)
                .collect(Collectors.groupingBy(a -> a.getMedicamento().getClave() + "|" + a.getTipo()));
        Map<Long, List<Alerta>> abiertasDeCaducidad = abiertas.stream()
                .filter(a -> a.getLote() != null && "CADUCIDAD_PROXIMA".equals(a.getTipo()))
                .collect(Collectors.groupingBy(a -> a.getLote().getId()));

        for (Medicamento medicamento : medicamentoRepository.findAll()) {
            RotacionResponse rotacion = calcular(medicamento, contexto);

            if ("SIN_CONSUMO".equals(rotacion.getEstado())) {
                continue; // sin historial de consumo no se puede evaluar un umbral confiable
            }

            actualizarAlerta(medicamento, "DESABASTO", "DESABASTO".equals(rotacion.getEstado()),
                    rotacion.getExistenciaActual(), rotacion.getStockMinimo(),
                    abiertasDeStock.getOrDefault(medicamento.getClave() + "|DESABASTO", List.of()));
            actualizarAlerta(medicamento, "SOBREABASTO", "SOBREABASTO".equals(rotacion.getEstado()),
                    rotacion.getExistenciaActual(), rotacion.getStockMaximo(),
                    abiertasDeStock.getOrDefault(medicamento.getClave() + "|SOBREABASTO", List.of()));
        }

        evaluarAlertasCaducidad(contexto.existenciaPorLote(), abiertasDeCaducidad);
    }

    private void actualizarAlerta(Medicamento medicamento, String tipo, boolean debeEstarActiva, int existenciaActual, double limite,
                                  List<Alerta> abiertasOrdenadas) {
        List<Alerta> abiertas = cerrarDuplicadas(abiertasOrdenadas);

        if (debeEstarActiva && abiertas.isEmpty()) {
            Alerta alerta = new Alerta();
            alerta.setMedicamento(medicamento);
            alerta.setTipo(tipo);
            alerta.setExistenciaAlInicio(existenciaActual);
            alerta.setLimiteAlInicio(limite);
            alertaRepository.save(alerta);
        } else if (!debeEstarActiva && !abiertas.isEmpty()) {
            Alerta alerta = abiertas.get(0);
            alerta.setFechaFin(LocalDateTime.now());
            alertaRepository.save(alerta);
        }
    }

    // ---------- Ajuste: alerta de caducidad proxima (por lote, no por clave) ----------

    /**
     * Revisa cada lote DISPONIBLE y abre o cierra su alerta de caducidad
     * proxima segun le queden menos de MESES_ALERTA_CADUCIDAD meses. Un lote
     * ya vencido no entra aqui: ese caso lo bloquea ServicioDespacho y lo
     * atiende HU19 (apartar caducados), no esta alerta.
     */
    private void evaluarAlertasCaducidad(Map<Long, Integer> existenciaPorLote, Map<Long, List<Alerta>> abiertasPorLote) {
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.plusMonths(MESES_ALERTA_CADUCIDAD);

        for (Lote lote : loteRepository.findByEstatus(ESTATUS_LOTE_DISPONIBLE)) {
            boolean porVencer = !lote.getCaducidad().isBefore(hoy) && lote.getCaducidad().isBefore(limite);
            int existenciaDelLote = existenciaPorLote.getOrDefault(lote.getId(), 0);

            // Un lote sin existencia (ya se despacho/canjeo todo) no amerita alerta.
            actualizarAlertaCaducidad(lote, porVencer && existenciaDelLote > 0, existenciaDelLote,
                    abiertasPorLote.getOrDefault(lote.getId(), List.of()));
        }
    }

    private void actualizarAlertaCaducidad(Lote lote, boolean debeEstarActiva, int existenciaActual, List<Alerta> abiertasOrdenadas) {
        List<Alerta> abiertas = cerrarDuplicadas(abiertasOrdenadas);

        if (debeEstarActiva && abiertas.isEmpty()) {
            Alerta alerta = new Alerta();
            alerta.setMedicamento(lote.getMedicamento());
            alerta.setLote(lote);
            alerta.setTipo("CADUCIDAD_PROXIMA");
            alerta.setExistenciaAlInicio(existenciaActual);
            alerta.setLimiteAlInicio(MESES_ALERTA_CADUCIDAD);
            alertaRepository.save(alerta);
        } else if (!debeEstarActiva && !abiertas.isEmpty()) {
            Alerta alerta = abiertas.get(0);
            alerta.setFechaFin(LocalDateTime.now());
            alertaRepository.save(alerta);
        }
    }

    /**
     * Autocuracion: si por una carrera entre dos evaluaciones concurrentes
     * llegaran a quedar varias alertas abiertas para la misma clave/lote y
     * tipo, se cierran todas menos la mas antigua, para que el sistema se
     * recupere solo sin intervencion manual en la base de datos.
     */
    private List<Alerta> cerrarDuplicadas(List<Alerta> abiertasOrdenadas) {
        if (abiertasOrdenadas.size() <= 1) {
            return abiertasOrdenadas;
        }
        List<Alerta> sobrantes = abiertasOrdenadas.subList(1, abiertasOrdenadas.size());
        for (Alerta duplicada : sobrantes) {
            duplicada.setFechaFin(LocalDateTime.now());
        }
        alertaRepository.saveAll(sobrantes);
        return abiertasOrdenadas.subList(0, 1);
    }
}
