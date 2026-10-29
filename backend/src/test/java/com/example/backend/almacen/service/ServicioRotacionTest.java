package com.example.backend.almacen.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU16 (consumo promedio mensual, CPM, con el consumo de Intelisis mientras
 * HAERO no tenga 6 meses de salidas, HU38) y HU17 (alertas de stock minimo y maximo).
 */
@ExtendWith(MockitoExtension.class)
class ServicioRotacionTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private ExistenciaRepository existenciaRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private LoteRepository loteRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate haero;

    private ServicioRotacion servicioRotacion;

    private Medicamento medicamento;

    @BeforeEach
    void configurar() {
        servicioRotacion = new ServicioRotacion(medicamentoRepository, existenciaRepository, movimientoRepository, loteRepository, alertaRepository, haero);

        medicamento = new Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");
    }

    private Movimiento salida(int cajas, String claveMedicamento, LocalDateTime fecha) {
        Medicamento m = new Medicamento();
        m.setClave(claveMedicamento);
        Lote lote = new Lote();
        lote.setMedicamento(m);
        Existencia existencia = new Existencia();
        existencia.setLote(lote);

        Movimiento movimiento = new Movimiento();
        movimiento.setTipo("SALIDA");
        movimiento.setCantidadCajas(cajas);
        movimiento.setExistencia(existencia);
        movimiento.setFecha(fecha);
        return movimiento;
    }

    private List<Existencia> existenciaVigente(String clave, int cantidad, LocalDate caducidad) {
        Medicamento m = new Medicamento();
        m.setClave(clave);
        Lote lote = new Lote();
        lote.setMedicamento(m);
        lote.setCaducidad(caducidad);
        Existencia existencia = new Existencia();
        existencia.setLote(lote);
        existencia.setCantidadCajas(cantidad);
        return List.of(existencia);
    }

    private void sinSalidasPrevias() {
        lenient().when(movimientoRepository.findFirstByTipoOrderByFechaAsc("SALIDA")).thenReturn(Optional.empty());
    }

    private void sistemaOperandoDesde(LocalDateTime primeraSalidaJamasRegistrada) {
        Movimiento primera = salida(1, "x", primeraSalidaJamasRegistrada);
        lenient().when(movimientoRepository.findFirstByTipoOrderByFechaAsc("SALIDA")).thenReturn(Optional.of(primera));
    }

    // ---------- HU16: historial parcial y consumo de Intelisis (HU38) ----------

    @Test
    void obtener_conSeisMesesCompletosDeHistorial_promediaEntreSeis() {
        LocalDateTime haceDosMeses = LocalDateTime.now().minusMonths(2);
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8)); // mas de 6 meses operando: ventana completa

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(120, "0134", haceDosMeses)));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().plusYears(1)));

        RotacionResponse respuesta = servicioRotacion.obtener("0134");

        assertThat(respuesta.getMesesHistorial()).isEqualTo(6);
        assertThat(respuesta.getCpm()).isEqualTo(20.0); // 120 / 6
        assertThat(respuesta.getOrigenCpm()).isEqualTo("SISTEMA");
    }

    @Test
    void obtener_conSoloDosMesesDeSistemaOperando_escalaEntreDosNoEntreSeis() {
        LocalDateTime haceUnMes = LocalDateTime.now().minusMonths(1);
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(2)); // el sistema apenas lleva 2 meses cerrados

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(40, "0134", haceUnMes)));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().plusYears(1)));

        RotacionResponse respuesta = servicioRotacion.obtener("0134");

        assertThat(respuesta.getMesesHistorial()).isEqualTo(2);
        assertThat(respuesta.getCpm()).isEqualTo(20.0); // 40 / 2, no 40 / 6
    }

    /** Simula la tabla intelisis_consumo_mes: {clave, mes AAAA-MM, cajas, piezas}. */
    private void consumoIntelisis(Object[]... renglones) {
        org.mockito.Mockito.doAnswer(invocacion -> {
            org.springframework.jdbc.core.RowCallbackHandler manejador = invocacion.getArgument(1);
            for (Object[] r : renglones) {
                java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
                when(rs.getString(1)).thenReturn((String) r[0]);
                when(rs.getString(2)).thenReturn((String) r[1]);
                when(rs.getDouble(3)).thenReturn(((Number) r[2]).doubleValue());
                when(rs.getDouble(4)).thenReturn(((Number) r[3]).doubleValue());
                manejador.processRow(rs);
            }
            return null;
        }).when(haero).query(org.mockito.ArgumentMatchers.contains("intelisis_consumo_mes"),
                any(org.springframework.jdbc.core.RowCallbackHandler.class), any(), any());
    }

    private static String mes(int haceMeses) {
        return java.time.YearMonth.now().minusMonths(haceMeses).toString();
    }

    @Test
    void obtener_sinSeisMesesEnHaero_usaElConsumoDeIntelisisEnCajas() {
        // HU38: 100 piezas de 10 por caja + 2 cajas de la clave base = 12 cajas en un mes; 48 cajas en otro.
        medicamento.setPiezasPorCaja(10);
        sinSalidasPrevias();
        consumoIntelisis(new Object[]{"0134", mes(1), 2, 100}, new Object[]{"0134", mes(3), 0, 480});

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 25, LocalDate.now().plusYears(1)));

        RotacionResponse respuesta = servicioRotacion.obtener("0134");

        assertThat(respuesta.getOrigenCpm()).isEqualTo("INTELISIS");
        assertThat(respuesta.getCpm()).isEqualTo(10.0); // (12 + 48) / 6 meses
        assertThat(respuesta.getEstado()).isEqualTo("DESABASTO"); // 25 cajas <= 3 meses de CPM
    }

    @Test
    void obtener_conSeisMesesEnHaero_yaNoUsaIntelisis() {
        medicamento.setPiezasPorCaja(10);
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8));
        lenient().doNothing().when(haero).query(org.mockito.ArgumentMatchers.anyString(),
                any(org.springframework.jdbc.core.RowCallbackHandler.class), any(), any());

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(60, "0134", LocalDateTime.now().minusMonths(2))));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().plusYears(1)));

        assertThat(servicioRotacion.obtener("0134").getOrigenCpm()).isEqualTo("SISTEMA");
    }

    @Test
    void obtener_conPiezasSinPiezasPorCaja_noInventaElCpm() {
        sinSalidasPrevias();
        consumoIntelisis(new Object[]{"0134", mes(1), 0, 100});

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().plusYears(1)));

        assertThat(servicioRotacion.obtener("0134").getEstado()).isEqualTo("SIN_CONSUMO");
    }

    @Test
    void obtener_sinSalidasYSinConsumoEnIntelisis_quedaSinConsumo() {
        sinSalidasPrevias();

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().plusYears(1)));

        RotacionResponse respuesta = servicioRotacion.obtener("0134");

        assertThat(respuesta.getCpm()).isZero();
        assertThat(respuesta.getEstado()).isEqualTo("SIN_CONSUMO");
    }

    @Test
    void obtener_excluyeLotesVencidosDeLaExistenciaActual() {
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8));
        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 100, LocalDate.now().minusDays(1))); // vencido

        RotacionResponse respuesta = servicioRotacion.obtener("0134");

        assertThat(respuesta.getExistenciaActual()).isZero();
    }

    @Test
    void obtener_conClaveInexistente_lanza404() {
        when(medicamentoRepository.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioRotacion.obtener("9999"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Medicamento no encontrado");
    }

    @Test
    void listar_devuelveUnRenglonPorCadaMedicamento() {
        Medicamento otro = new Medicamento();
        otro.setClave("0210");
        otro.setNombreGenerico("Insulina glargina");

        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento, otro));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        when(existenciaRepository.findAllConLote()).thenReturn(List.of());

        List<RotacionResponse> respuesta = servicioRotacion.listar();

        assertThat(respuesta).extracting(RotacionResponse::getClave).containsExactly("0134", "0210");
    }

    // ---------- HU17: alertas ----------

    @Test
    void evaluarAlertas_conExistenciaPorDebajoDelMinimo_abreAlertaDesabasto() {
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8));
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento));
        // CPM = 60/6 = 10 -> minimo 30, maximo 60
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(60, "0134", LocalDateTime.now().minusMonths(1))));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 20, LocalDate.now().plusYears(1))); // por debajo de 30
        when(alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()).thenReturn(List.of());

        servicioRotacion.evaluarAlertas();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("DESABASTO");
        assertThat(captor.getValue().getMedicamento().getClave()).isEqualTo("0134");
        assertThat(captor.getValue().getExistenciaAlInicio()).isEqualTo(20);
        assertThat(captor.getValue().getLimiteAlInicio()).isEqualTo(30.0);
    }

    @Test
    void evaluarAlertas_conExistenciaPorEncimaDelMaximo_abreAlertaSobreabasto() {
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8));
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(60, "0134", LocalDateTime.now().minusMonths(1))));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 200, LocalDate.now().plusYears(1))); // por encima de 60
        when(alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()).thenReturn(List.of());

        servicioRotacion.evaluarAlertas();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("SOBREABASTO");
    }

    @Test
    void evaluarAlertas_conExistenciaQueVuelveAlRango_cierraLaAlertaAbierta() {
        sistemaOperandoDesde(LocalDateTime.now().minusMonths(8));
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(salida(60, "0134", LocalDateTime.now().minusMonths(1))));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaVigente("0134", 45, LocalDate.now().plusYears(1))); // entre 30 y 60

        Alerta alertaAbierta = new Alerta();
        alertaAbierta.setMedicamento(medicamento);
        alertaAbierta.setTipo("DESABASTO");
        when(alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()).thenReturn(List.of(alertaAbierta));

        servicioRotacion.evaluarAlertas();

        assertThat(alertaAbierta.getFechaFin()).isNotNull();
        verify(alertaRepository).save(alertaAbierta);
    }

    @Test
    void evaluarAlertas_sinHistorialDeConsumo_noEvaluaUmbralesNiAbreAlertas() {
        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento));
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());
        lenient().when(existenciaRepository.findAllConLote()).thenReturn(List.of());

        servicioRotacion.evaluarAlertas();

        verify(alertaRepository, never()).findByMedicamentoClaveAndTipoAndFechaFinIsNullOrderByFechaInicioAsc(any(), any());
        verify(alertaRepository, never()).save(any());
    }

    // ---------- Ajuste: alerta de caducidad proxima (por lote) ----------

    private Lote lote(Long id, String clave, LocalDate caducidad) {
        Lote lote = new Lote();
        lote.setId(id);
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(clave);
        lote.setCaducidad(caducidad);
        lote.setEstatus("DISPONIBLE");
        return lote;
    }

    private List<Existencia> existenciaDelLote(Lote lote, int cajas) {
        Existencia existencia = new Existencia();
        existencia.setLote(lote);
        existencia.setCantidadCajas(cajas);
        return List.of(existencia);
    }

    @Test
    void evaluarAlertas_conLoteAMenosDeNueveMesesDeCaducar_abreAlertaCaducidadProxima() {
        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of());
        Lote lote = lote(1L, "L-0001", LocalDate.now().plusMonths(3));
        when(loteRepository.findByEstatus("DISPONIBLE")).thenReturn(List.of(lote));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaDelLote(lote, 40));

        servicioRotacion.evaluarAlertas();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("CADUCIDAD_PROXIMA");
        assertThat(captor.getValue().getLote()).isEqualTo(lote);
        assertThat(captor.getValue().getExistenciaAlInicio()).isEqualTo(40);
    }

    @Test
    void evaluarAlertas_conLoteAMasDeNueveMesesDeCaducar_noAbreAlerta() {
        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of());
        Lote lote = lote(2L, "L-0002", LocalDate.now().plusMonths(12));
        when(loteRepository.findByEstatus("DISPONIBLE")).thenReturn(List.of(lote));
        when(existenciaRepository.findAllConLote()).thenReturn(existenciaDelLote(lote, 40));

        servicioRotacion.evaluarAlertas();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void evaluarAlertas_conLoteYaVencido_noLoEvaluaAqui() {
        // Un lote vencido lo bloquea ServicioDespacho y lo atiende HU19 (apartar
        // caducados); esta alerta es solo para lotes TODAVIA vigentes.
        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of());
        Lote lote = lote(3L, "L-0003", LocalDate.now().minusDays(1));
        when(loteRepository.findByEstatus("DISPONIBLE")).thenReturn(List.of(lote));

        servicioRotacion.evaluarAlertas();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void evaluarAlertas_conLoteQueYaNoTieneExistencia_cierraLaAlertaAbierta() {
        sinSalidasPrevias();
        when(medicamentoRepository.findAll()).thenReturn(List.of());
        Lote lote = lote(4L, "L-0004", LocalDate.now().plusMonths(3));
        when(loteRepository.findByEstatus("DISPONIBLE")).thenReturn(List.of(lote));
        when(existenciaRepository.findAllConLote()).thenReturn(List.of()); // ya se despacho/canjeo todo

        Alerta alertaAbierta = new Alerta();
        alertaAbierta.setMedicamento(medicamento);
        alertaAbierta.setLote(lote);
        alertaAbierta.setTipo("CADUCIDAD_PROXIMA");
        when(alertaRepository.findByFechaFinIsNullOrderByFechaInicioDesc()).thenReturn(List.of(alertaAbierta));

        servicioRotacion.evaluarAlertas();

        assertThat(alertaAbierta.getFechaFin()).isNotNull();
        verify(alertaRepository).save(alertaAbierta);
    }
}
