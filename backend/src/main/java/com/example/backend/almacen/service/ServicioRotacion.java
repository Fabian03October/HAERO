package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.AlertaResponse;
import com.example.backend.almacen.dto.ConsumoMesResponse;
import com.example.backend.almacen.dto.RotacionResponse;
import com.example.backend.almacen.entity.Alerta;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.AlertaRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
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
    private static final String TIPO_SALIDA_FARMACIA = "SALIDA";
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MedicamentoRepository medicamentoRepository;
    private final ExistenciaRepository existenciaRepository;
    private final MovimientoRepository movimientoRepository;
    private final AlertaRepository alertaRepository;

    public ServicioRotacion(MedicamentoRepository medicamentoRepository,
                             ExistenciaRepository existenciaRepository,
                             MovimientoRepository movimientoRepository,
                             AlertaRepository alertaRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.existenciaRepository = existenciaRepository;
        this.movimientoRepository = movimientoRepository;
        this.alertaRepository = alertaRepository;
    }

    // ---------- HU16: CPM ----------

    public List<RotacionResponse> listar() {
        List<YearMonth> ventana = ventana(LocalDate.now());
        int mesesHistorial = mesesHistorial(ventana);

        return medicamentoRepository.findAll().stream()
                .map(m -> calcular(m, ventana, mesesHistorial))
                .toList();
    }

    public RotacionResponse obtener(String clave) {
        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));

        List<YearMonth> ventana = ventana(LocalDate.now());
        return calcular(medicamento, ventana, mesesHistorial(ventana));
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

    private RotacionResponse calcular(Medicamento medicamento, List<YearMonth> ventana, int mesesHistorial) {
        String clave = medicamento.getClave();
        LocalDateTime desde = ventana.get(0).atDay(1).atStartOfDay();
        LocalDateTime hasta = ventana.get(ventana.size() - 1).atEndOfMonth().atTime(23, 59, 59, 999_000_000);

        List<Movimiento> salidasDeLaClave = movimientoRepository.findByFechaBetweenOrderByFechaDesc(desde, hasta).stream()
                .filter(m -> TIPO_SALIDA_FARMACIA.equals(m.getTipo()))
                .filter(m -> m.getExistencia().getLote().getMedicamento().getClave().equals(clave))
                .toList();

        Map<YearMonth, Integer> cajasPorMes = salidasDeLaClave.stream()
                .collect(Collectors.groupingBy(m -> YearMonth.from(m.getFecha()), Collectors.summingInt(Movimiento::getCantidadCajas)));

        List<ConsumoMesResponse> meses = ventana.stream()
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

        int existenciaActual = existenciaVigente(clave);
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

    /** Suma la existencia de los lotes de la clave, sin contar los ya vencidos (aunque sigan "DISPONIBLE"). */
    private int existenciaVigente(String medicamentoClave) {
        LocalDate hoy = LocalDate.now();
        return existenciaRepository.findAll().stream()
                .filter(e -> e.getLote().getMedicamento().getClave().equals(medicamentoClave))
                .filter(e -> !e.getLote().getCaducidad().isBefore(hoy))
                .mapToInt(Existencia::getCantidadCajas)
                .sum();
    }

    // ---------- HU17: alertas de stock minimo y maximo ----------

    public List<AlertaResponse> listarAlertas(boolean activas) {
        evaluarAlertas(); // mantiene el estado al dia en cada consulta; el @Scheduled es solo el respaldo

        List<Alerta> alertas = activas
                ? alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()
                : alertaRepository.findByFechaFinIsNotNullOrderByFechaFinDesc();

        return alertas.stream()
                .map(a -> new AlertaResponse(a.getId(), a.getMedicamento().getClave(), a.getMedicamento().getNombreGenerico(),
                        a.getTipo(), a.getFechaInicio(), a.getFechaFin(), a.getExistenciaAlInicio(), a.getLimiteAlInicio()))
                .toList();
    }

    /**
     * Recalcula el estado de cada clave y abre o cierra las alertas de stock
     * segun corresponda (HU17 CA2 a CA4). Se ejecuta cada hora para que las
     * alertas esten disponibles sin que nadie este conectado cuando se
     * generan, y tambien puede llamarse directo (por ejemplo en pruebas).
     */
    @Scheduled(fixedDelayString = "PT1H")
    public void evaluarAlertas() {
        List<YearMonth> ventana = ventana(LocalDate.now());
        int mesesHistorial = mesesHistorial(ventana);

        for (Medicamento medicamento : medicamentoRepository.findAll()) {
            RotacionResponse rotacion = calcular(medicamento, ventana, mesesHistorial);

            if ("SIN_CONSUMO".equals(rotacion.getEstado())) {
                continue; // sin historial de consumo no se puede evaluar un umbral confiable
            }

            actualizarAlerta(medicamento, "DESABASTO", "DESABASTO".equals(rotacion.getEstado()),
                    rotacion.getExistenciaActual(), rotacion.getStockMinimo());
            actualizarAlerta(medicamento, "SOBREABASTO", "SOBREABASTO".equals(rotacion.getEstado()),
                    rotacion.getExistenciaActual(), rotacion.getStockMaximo());
        }
    }

    private void actualizarAlerta(Medicamento medicamento, String tipo, boolean debeEstarActiva, int existenciaActual, double limite) {
        var alertaAbierta = alertaRepository.findByMedicamentoClaveAndTipoAndFechaFinIsNull(medicamento.getClave(), tipo);

        if (debeEstarActiva && alertaAbierta.isEmpty()) {
            Alerta alerta = new Alerta();
            alerta.setMedicamento(medicamento);
            alerta.setTipo(tipo);
            alerta.setExistenciaAlInicio(existenciaActual);
            alerta.setLimiteAlInicio(limite);
            alertaRepository.save(alerta);
        } else if (!debeEstarActiva && alertaAbierta.isPresent()) {
            Alerta alerta = alertaAbierta.get();
            alerta.setFechaFin(LocalDateTime.now());
            alertaRepository.save(alerta);
        }
    }
}
