package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistrarEntradaResponse;
import com.example.backend.almacen.dto.RegistroIntelisisResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.entity.PedidoDetalle;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.almacen.repository.PedidoDetalleRepository;
import com.example.backend.almacen.repository.PedidoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU10 y HU21 con el diseño aprobado: se escanea la caja, se elige el registro de
 * Intelisis y Almacén solo pone ubicación y cajas. Lote y caducidad salen de Intelisis.
 */
@ExtendWith(MockitoExtension.class)
class ServicioRecepcionTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private PedidoDetalleRepository pedidoDetalleRepository;
    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private CodigoBarrasRepository codigoBarrasRepository;
    @Mock
    private LoteRepository loteRepository;
    @Mock
    private ExistenciaRepository existenciaRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ServicioRegistrosIntelisis servicioRegistrosIntelisis;

    private ServicioRecepcion servicioRecepcion;

    private Medicamento medicamento;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicioRecepcion = new ServicioRecepcion(pedidoRepository, pedidoDetalleRepository, medicamentoRepository,
                codigoBarrasRepository, loteRepository, existenciaRepository, movimientoRepository, usuarioRepository,
                servicioRegistrosIntelisis);

        medicamento = new Medicamento();
        medicamento.setClave("010.000.0104.00");
        medicamento.setNombreGenerico("Paracetamol");
        medicamento.setPresentacion("Envase con 10 tabletas");

        usuario = new Usuario();
        usuario.setNombreUsuario("almacen");
    }

    private RegistrarEntradaRequest request(String origen, Integer idIntelisis, int... cajas) {
        RegistrarEntradaRequest request = new RegistrarEntradaRequest();
        request.setCodigo("7501234567890");
        request.setClave("010.000.0104.00");
        request.setOrigen(origen);
        request.setIdIntelisis(idIntelisis);
        request.setLote("L-8821");
        List<UbicacionCantidad> ubicaciones = new java.util.ArrayList<>();
        for (int i = 0; i < cajas.length; i++) {
            UbicacionCantidad ubicacion = new UbicacionCantidad();
            ubicacion.setUbicacion("Sector " + (i + 5));
            ubicacion.setCajas(cajas[i]);
            ubicaciones.add(ubicacion);
        }
        request.setUbicaciones(ubicaciones);
        return request;
    }

    private static RegistroIntelisisResponse compra(Long pedidoId, String bloqueo) {
        return new RegistroIntelisisResponse("COMPRA", 5001, "Ent. Compra Farmacia 7788", null, LocalDate.of(2026, 10, 13),
                "010.000.0104.00", "L-8821", LocalDate.of(2028, 7, 31), new BigDecimal("650"), new BigDecimal("65"),
                "Lab Sanfer", pedidoId, "PP-8632", "IB-HRAEO-AD-B-C-310-2025", bloqueo, null);
    }

    private void guardadoNormal() {
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(loteRepository.save(any(Lote.class))).thenAnswer(inv -> {
            Lote lote = inv.getArgument(0);
            lote.setId(10L);
            return lote;
        });
        when(existenciaRepository.save(any(Existencia.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void resolverCodigo_noRegistrado_lanza404ConIndicacionDeAsociar() {
        when(codigoBarrasRepository.findById("750000000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioRecepcion.resolverCodigo("750000000001"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("asóci");
    }

    @Test
    void registrarEntrada_sinCodigoDeBarras_lanza400() {
        RegistrarEntradaRequest request = request("COMPRA", 5001, 40);
        request.setCodigo(" ");

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request, "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("código de barras");
        verify(loteRepository, never()).save(any());
    }

    @Test
    void registrarEntrada_sinRegistroEnIntelisis_lanza400YNoCreaNada() {
        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(medicamento));
        when(servicioRegistrosIntelisis.buscar("COMPRA", 5001, "010.000.0104.00", "L-8821")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request("COMPRA", 5001, 40), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no está en Intelisis");
        verify(loteRepository, never()).save(any());
        verify(codigoBarrasRepository, never()).save(any());
    }

    @Test
    void registrarEntrada_dePedidoCanceladoEnIntelisis_lanza400YNoCreaNada() {
        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(medicamento));
        when(servicioRegistrosIntelisis.buscar("COMPRA", 5001, "010.000.0104.00", "L-8821"))
                .thenReturn(Optional.of(compra(1L, "El pedido PP-8632 está cancelado en Intelisis: no recibir.")));

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request("COMPRA", 5001, 40), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelado");
        verify(loteRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
    }

    @Test
    void registrarEntrada_conCodigoDeOtraClave_lanza400() {
        Medicamento otro = new Medicamento();
        otro.setClave("010.000.6282.00");
        CodigoBarras codigo = new CodigoBarras();
        codigo.setCodigo("7501234567890");
        codigo.setMedicamento(otro);

        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(medicamento));
        when(servicioRegistrosIntelisis.buscar("COMPRA", 5001, "010.000.0104.00", "L-8821")).thenReturn(Optional.of(compra(1L, null)));
        when(codigoBarrasRepository.findById("7501234567890")).thenReturn(Optional.of(codigo));

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request("COMPRA", 5001, 40), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("010.000.6282.00");
        verify(loteRepository, never()).save(any());
    }

    @Test
    void registrarEntrada_deCompra_enDosUbicaciones_guardaLaFotoDeIntelisisAsociaElCodigoYSumaAlPedido() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setEstatus("ACTIVO");
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setPedido(pedido);
        detalle.setMedicamento(medicamento);
        detalle.setCantidadEsperada(100);
        detalle.setCantidadRecibida(0);

        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(medicamento));
        when(servicioRegistrosIntelisis.buscar("COMPRA", 5001, "010.000.0104.00", "L-8821")).thenReturn(Optional.of(compra(1L, null)));
        when(codigoBarrasRepository.findById("7501234567890")).thenReturn(Optional.empty());
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoDetalleRepository.findByPedidoIdAndMedicamentoClave(1L, "010.000.0104.00")).thenReturn(Optional.of(detalle));
        guardadoNormal();

        // Almacén contó 62 cajas; Intelisis dice 65: la diferencia queda registrada.
        RegistrarEntradaResponse respuesta = servicioRecepcion.registrarEntrada(request("COMPRA", 5001, 40, 22), "almacen");

        assertThat(respuesta.getLoteId()).isEqualTo(10L);
        assertThat(respuesta.getCajasConfirmadas()).isEqualTo(62);
        assertThat(respuesta.getCajasIntelisis()).isEqualByComparingTo("65");

        ArgumentCaptor<Lote> loteCaptor = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository).save(loteCaptor.capture());
        Lote lote = loteCaptor.getValue();
        assertThat(lote.getNumeroLote()).isEqualTo("L-8821");
        assertThat(lote.getCaducidad()).isEqualTo(LocalDate.of(2028, 7, 31));
        assertThat(lote.getProveedor()).isEqualTo("Lab Sanfer");
        assertThat(lote.getPedido()).isSameAs(pedido);
        assertThat(lote.getOrigenIntelisis()).isEqualTo("COMPRA");
        assertThat(lote.getIdIntelisis()).isEqualTo(5001);
        assertThat(lote.getMovimientoIntelisis()).isEqualTo("Ent. Compra Farmacia 7788");
        assertThat(lote.getContrato()).isEqualTo("IB-HRAEO-AD-B-C-310-2025");
        assertThat(lote.getCajasConfirmadas()).isEqualTo(62);

        ArgumentCaptor<CodigoBarras> codigoCaptor = ArgumentCaptor.forClass(CodigoBarras.class);
        verify(codigoBarrasRepository).save(codigoCaptor.capture());
        assertThat(codigoCaptor.getValue().getMedicamento()).isSameAs(medicamento);

        verify(existenciaRepository, times(2)).save(any(Existencia.class));
        ArgumentCaptor<Movimiento> movimientoCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(2)).save(movimientoCaptor.capture());
        assertThat(movimientoCaptor.getAllValues()).allSatisfy(m -> assertThat(m.getTipo()).isEqualTo("ENTRADA"));

        assertThat(detalle.getCantidadRecibida()).isEqualTo(62);
        verify(pedidoDetalleRepository).save(detalle);
    }

    @Test
    void registrarEntrada_altaInicialDeLaExistencia_noTocaPedidosYQuedaComoCargaInicial() {
        RegistroIntelisisResponse existencia = new RegistroIntelisisResponse("EXISTENCIA", null, "Existencia 25301", null,
                LocalDate.of(2026, 9, 1), "010.000.0104.00", "L-8821", LocalDate.of(2028, 7, 31), new BigDecimal("300"),
                new BigDecimal("30"), null, null, null, null, null, null);
        CodigoBarras codigo = new CodigoBarras();
        codigo.setCodigo("7501234567890");
        codigo.setMedicamento(medicamento);

        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(medicamento));
        when(servicioRegistrosIntelisis.buscar("EXISTENCIA", null, "010.000.0104.00", "L-8821")).thenReturn(Optional.of(existencia));
        when(codigoBarrasRepository.findById("7501234567890")).thenReturn(Optional.of(codigo));
        guardadoNormal();

        servicioRecepcion.registrarEntrada(request("EXISTENCIA", null, 30), "almacen");

        ArgumentCaptor<Lote> loteCaptor = ArgumentCaptor.forClass(Lote.class);
        verify(loteRepository).save(loteCaptor.capture());
        assertThat(loteCaptor.getValue().getOrigenIntelisis()).isEqualTo("EXISTENCIA");
        assertThat(loteCaptor.getValue().getProveedor()).isEqualTo("Sin proveedor en Intelisis");
        assertThat(loteCaptor.getValue().getPedido()).isNull();

        ArgumentCaptor<Movimiento> movimientoCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(movimientoCaptor.capture());
        assertThat(movimientoCaptor.getValue().getTipo()).isEqualTo("CARGA_INICIAL");
        // El código ya era de esta clave: no se vuelve a guardar.
        verify(codigoBarrasRepository, never()).save(any());
        verify(pedidoDetalleRepository, never()).save(any());
    }
}
