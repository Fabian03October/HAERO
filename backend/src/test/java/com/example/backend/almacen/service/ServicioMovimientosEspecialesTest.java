package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CaducadoResponse;
import com.example.backend.almacen.dto.CanjeRequest;
import com.example.backend.almacen.dto.CanjeResponse;
import com.example.backend.almacen.dto.MovimientoExternoRequest;
import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU18 (canje), HU19 (caducados) y HU20 (prestamos y transferencias).
 */
@ExtendWith(MockitoExtension.class)
class ServicioMovimientosEspecialesTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private LoteRepository loteRepository;

    @Mock
    private ExistenciaRepository existenciaRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private ServicioMovimientosEspeciales servicio;

    private final Usuario usuario = new Usuario();
    private final Medicamento medicamento = new Medicamento();

    @BeforeEach
    void configurar() {
        servicio = new ServicioMovimientosEspeciales(medicamentoRepository, loteRepository, existenciaRepository,
                movimientoRepository, usuarioRepository, new ServicioMovimientos(movimientoRepository));
        usuario.setNombreUsuario("almacen");
        usuario.setNombreCompleto("Encargada de Almacén");
        medicamento.setClave("010.000.6059.01-1");
        medicamento.setNombreGenerico("Lactulosa");
        lenient().when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        lenient().when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(existenciaRepository.save(any(Existencia.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(loteRepository.save(any(Lote.class))).thenAnswer(invocacion -> {
            Lote lote = invocacion.getArgument(0);
            if (lote.getId() == null) {
                lote.setId(99L);
            }
            return lote;
        });
    }

    // ---------- HU18 ----------

    @Test
    void canje_retiraElLoteDeOrigenYDaDeAltaElNuevoLigadoAEl() {
        Lote origen = lote(1L, "L-ORIGEN", LocalDate.now().plusMonths(3), "DISPONIBLE");
        Existencia existenciaOrigen = existencia(10L, origen, "SECTOR 4", 40);
        when(loteRepository.findById(1L)).thenReturn(Optional.of(origen));
        when(existenciaRepository.findByLoteId(1L)).thenReturn(List.of(existenciaOrigen));

        CanjeResponse respuesta = servicio.registrarCanje(canje(1L, "L-NUEVO", LocalDate.now().plusYears(2), 40), "almacen");

        assertThat(origen.getEstatus()).isEqualTo("CANJEADO");
        assertThat(existenciaOrigen.getCantidadCajas()).isZero();
        assertThat(respuesta.getCajasOrigen()).isEqualTo(40);
        assertThat(respuesta.getCajasNuevas()).isEqualTo(40);
        assertThat(respuesta.getLoteOrigen()).isEqualTo("L-ORIGEN");
        assertThat(respuesta.getLoteNuevo()).isEqualTo("L-NUEVO");

        ArgumentCaptor<Lote> lotes = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository, atLeastOnce()).save(lotes.capture());
        Lote nuevo = lotes.getAllValues().stream().filter(l -> "L-NUEVO".equals(l.getNumeroLote())).findFirst().orElseThrow();
        assertThat(nuevo.getLoteOrigen()).isSameAs(origen);
        assertThat(nuevo.getEstatus()).isEqualTo("DISPONIBLE");
    }

    @Test
    void canje_deLoteConNueveMesesOMasDeVida_lanza400() {
        Lote origen = lote(1L, "L-ORIGEN", LocalDate.now().plusMonths(12), "DISPONIBLE");
        when(loteRepository.findById(1L)).thenReturn(Optional.of(origen));

        assertThatThrownBy(() -> servicio.registrarCanje(canje(1L, "L-NUEVO", LocalDate.now().plusYears(3), 10), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("menos de 9 meses");

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void canje_deLoteYaCanjeado_lanza400() {
        Lote origen = lote(1L, "L-ORIGEN", LocalDate.now().plusMonths(3), "CANJEADO");
        when(loteRepository.findById(1L)).thenReturn(Optional.of(origen));

        assertThatThrownBy(() -> servicio.registrarCanje(canje(1L, "L-NUEVO", LocalDate.now().plusYears(2), 10), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya no está disponible");
    }

    // ---------- HU19 ----------

    @Test
    void moverACaducados_loteVencido_loApartaYDejaLaExistenciaEnCero() {
        Lote vencido = lote(2L, "L-VENCIDO", LocalDate.now().minusDays(1), "DISPONIBLE");
        Existencia existencia = existencia(20L, vencido, "BA ANAQUEL 1", 12);
        when(loteRepository.findById(2L)).thenReturn(Optional.of(vencido));
        when(existenciaRepository.findByLoteId(2L)).thenReturn(List.of(existencia));

        CaducadoResponse respuesta = servicio.moverACaducados(2L, "almacen");

        assertThat(vencido.getEstatus()).isEqualTo("CADUCADO");
        assertThat(existencia.getCantidadCajas()).isZero();
        assertThat(respuesta.getCajas()).isEqualTo(12);
        ArgumentCaptor<Movimiento> movimiento = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertThat(movimiento.getValue().getTipo()).isEqualTo("BAJA_CADUCIDAD");
    }

    @Test
    void moverACaducados_loteNoVencido_lanza400() {
        Lote vigente = lote(3L, "L-VIGENTE", LocalDate.now().plusMonths(2), "DISPONIBLE");
        when(loteRepository.findById(3L)).thenReturn(Optional.of(vigente));

        assertThatThrownBy(() -> servicio.moverACaducados(3L, "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("todavía no caduca");

        verify(movimientoRepository, never()).save(any());
    }

    // ---------- HU20 ----------

    @Test
    void prestamoDeSalida_descuentaLaExistenciaYGuardaLaInstitucion() {
        Lote lote = lote(4L, "L-1", LocalDate.now().plusYears(1), "DISPONIBLE");
        Existencia existencia = existencia(40L, lote, "2-A", 50);
        when(existenciaRepository.findById(40L)).thenReturn(Optional.of(existencia));

        MovimientoResponse respuesta = servicio.registrarExterno(externo("PRESTAMO", "SALIDA", "Hospital General", 20, 40L), "almacen");

        assertThat(existencia.getCantidadCajas()).isEqualTo(30);
        assertThat(respuesta.getTipo()).isEqualTo("PRESTAMO");
        assertThat(respuesta.getSentido()).isEqualTo("SALIDA");
        assertThat(respuesta.getInstitucion()).isEqualTo("Hospital General");
        assertThat(respuesta.getCajas()).isEqualTo(20);
    }

    @Test
    void prestamoDeSalida_conExistenciaInsuficiente_lanza400() {
        Lote lote = lote(4L, "L-1", LocalDate.now().plusYears(1), "DISPONIBLE");
        when(existenciaRepository.findById(40L)).thenReturn(Optional.of(existencia(40L, lote, "2-A", 5)));

        assertThatThrownBy(() -> servicio.registrarExterno(externo("PRESTAMO", "SALIDA", "Hospital General", 20, 40L), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Existencia insuficiente");

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void movimientoExterno_sinInstitucion_lanza400() {
        assertThatThrownBy(() -> servicio.registrarExterno(externo("TRANSFERENCIA", "SALIDA", "  ", 5, 40L), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("institución");
    }

    @Test
    void transferenciaDeEntrada_deLoteNuevo_loDaDeAltaConSuExistencia() {
        MovimientoExternoRequest request = externo("TRANSFERENCIA", "ENTRADA", "IMSS-Bienestar", 15, null);
        request.setClave("010.000.6059.01-1");
        request.setNumeroLote("L-EXT");
        request.setCaducidad(LocalDate.now().plusYears(1));
        request.setUbicacion("SECTOR 2");
        when(medicamentoRepository.findById("010.000.6059.01-1")).thenReturn(Optional.of(medicamento));
        when(loteRepository.findByMedicamentoClaveAndNumeroLoteIgnoreCaseAndEstatus("010.000.6059.01-1", "L-EXT", "DISPONIBLE")).thenReturn(List.of());
        when(existenciaRepository.findByLoteId(99L)).thenReturn(List.of());

        MovimientoResponse respuesta = servicio.registrarExterno(request, "almacen");

        assertThat(respuesta.getSentido()).isEqualTo("ENTRADA");
        assertThat(respuesta.getNumeroLote()).isEqualTo("L-EXT");
        assertThat(respuesta.getUbicacion()).isEqualTo("SECTOR 2");
        assertThat(respuesta.getCajas()).isEqualTo(15);
    }

    @Test
    void mesesDeVida_cuentaMesesCalendarioComoElFrontend() {
        LocalDate hoy = LocalDate.of(2026, 10, 1);
        assertThat(ServicioMovimientosEspeciales.mesesDeVida(LocalDate.of(2027, 6, 30), hoy)).isEqualTo(8);
        assertThat(ServicioMovimientosEspeciales.mesesDeVida(LocalDate.of(2027, 7, 1), hoy)).isEqualTo(9);
    }

    // ---------- datos de prueba ----------

    private Lote lote(Long id, String numero, LocalDate caducidad, String estatus) {
        Lote lote = new Lote();
        lote.setId(id);
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(numero);
        lote.setCaducidad(caducidad);
        lote.setProveedor("Laboratorios Sanfer");
        lote.setEstatus(estatus);
        return lote;
    }

    private Existencia existencia(Long id, Lote lote, String ubicacion, int cajas) {
        Existencia existencia = new Existencia();
        existencia.setId(id);
        existencia.setLote(lote);
        existencia.setUbicacion(ubicacion);
        existencia.setCantidadCajas(cajas);
        return existencia;
    }

    private CanjeRequest canje(Long loteOrigenId, String numeroLote, LocalDate caducidad, int cajas) {
        UbicacionCantidad ubicacion = new UbicacionCantidad();
        ubicacion.setUbicacion("SECTOR 4");
        ubicacion.setCajas(cajas);
        CanjeRequest request = new CanjeRequest();
        request.setLoteOrigenId(loteOrigenId);
        request.setNumeroLote(numeroLote);
        request.setCaducidad(caducidad);
        request.setUbicaciones(List.of(ubicacion));
        return request;
    }

    private MovimientoExternoRequest externo(String tipo, String sentido, String institucion, int cajas, Long existenciaId) {
        MovimientoExternoRequest request = new MovimientoExternoRequest();
        request.setTipo(tipo);
        request.setSentido(sentido);
        request.setInstitucion(institucion);
        request.setCajas(cajas);
        request.setExistenciaId(existenciaId);
        return request;
    }
}
