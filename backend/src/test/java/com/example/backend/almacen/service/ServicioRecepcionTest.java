package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.RegistrarEntradaRequest;
import com.example.backend.almacen.dto.RegistrarEntradaResponse;
import com.example.backend.almacen.dto.UbicacionCantidad;
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
 * PU-02 a PU-05 (HU10, HU11): recepcion de medicamento con lector de codigo de barras.
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

    private ServicioRecepcion servicioRecepcion;

    private Medicamento medicamento;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicioRecepcion = new ServicioRecepcion(pedidoRepository, pedidoDetalleRepository, medicamentoRepository,
                codigoBarrasRepository, loteRepository, existenciaRepository, movimientoRepository, usuarioRepository);

        medicamento = new Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");
        medicamento.setPresentacion("Caja con 20 tabletas");

        usuario = new Usuario();
        usuario.setNombreUsuario("despachador1");
    }

    private RegistrarEntradaRequest requestBase(Long pedidoId) {
        RegistrarEntradaRequest request = new RegistrarEntradaRequest();
        request.setPedidoId(pedidoId);
        request.setClave("0134");
        request.setProveedor("Lab Sanfer");
        request.setNumeroLote("L-8821");
        request.setCaducidad(LocalDate.of(2028, 7, 1));

        UbicacionCantidad ubicacion = new UbicacionCantidad();
        ubicacion.setUbicacion("Sector 5");
        ubicacion.setCajas(40);
        request.setUbicaciones(List.of(ubicacion));
        return request;
    }

    // ---------- PU-03: escanear codigo no registrado ----------

    @Test
    void resolverCodigo_noRegistrado_lanza404ConIndicacionDeAsociar() {
        when(codigoBarrasRepository.findById("750000000001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioRecepcion.resolverCodigo("750000000001"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("asóci");
    }

    // ---------- PU-02: entrada contra pedido CANCELADO ----------

    @Test
    void registrarEntrada_contraPedidoCancelado_lanza400YNoCreaNada() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setEstatus("CANCELADO");

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        RegistrarEntradaRequest request = requestBase(1L);

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelado");

        verify(loteRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
    }

    // ---------- PU-04: clave que no viene en el pedido ----------

    @Test
    void registrarEntrada_conClaveFueraDelPedido_lanza400YNadaGuardado() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setEstatus("ACTIVO");

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoDetalleRepository.findByPedidoIdAndMedicamentoClave(1L, "0134")).thenReturn(Optional.empty());

        RegistrarEntradaRequest request = requestBase(1L);

        assertThatThrownBy(() -> servicioRecepcion.registrarEntrada(request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no forma parte del pedido");

        verify(loteRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
    }

    // ---------- PU-05: entrada valida repartida en dos ubicaciones ----------

    @Test
    void registrarEntrada_valida_repartidaEnDosUbicaciones_creaUnLoteDosExistenciasYDosMovimientos() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setEstatus("ACTIVO");

        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setPedido(pedido);
        detalle.setMedicamento(medicamento);
        detalle.setCantidadEsperada(100);
        detalle.setCantidadRecibida(0);

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoDetalleRepository.findByPedidoIdAndMedicamentoClave(1L, "0134")).thenReturn(Optional.of(detalle));
        when(usuarioRepository.findByNombreUsuario("despachador1")).thenReturn(Optional.of(usuario));

        when(loteRepository.save(any(Lote.class))).thenAnswer(inv -> {
            Lote lote = inv.getArgument(0);
            lote.setId(10L);
            return lote;
        });
        when(existenciaRepository.save(any(Existencia.class))).thenAnswer(inv -> {
            Existencia existencia = inv.getArgument(0);
            existencia.setId((long) (100 + existencia.getCantidadCajas()));
            return existencia;
        });

        RegistrarEntradaRequest request = requestBase(1L);
        UbicacionCantidad ubicacion2 = new UbicacionCantidad();
        ubicacion2.setUbicacion("Sector 8");
        ubicacion2.setCajas(25);
        request.setUbicaciones(List.of(request.getUbicaciones().get(0), ubicacion2));

        RegistrarEntradaResponse respuesta = servicioRecepcion.registrarEntrada(request, "despachador1");

        assertThat(respuesta.getLoteId()).isEqualTo(10L);
        assertThat(respuesta.getExistencias()).hasSize(2);

        verify(loteRepository, times(1)).save(any(Lote.class));
        verify(existenciaRepository, times(2)).save(any(Existencia.class));

        ArgumentCaptor<Movimiento> movimientoCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository, times(2)).save(movimientoCaptor.capture());
        assertThat(movimientoCaptor.getAllValues()).allSatisfy(m -> assertThat(m.getTipo()).isEqualTo("ENTRADA"));

        ArgumentCaptor<PedidoDetalle> detalleCaptor = ArgumentCaptor.forClass(PedidoDetalle.class);
        verify(pedidoDetalleRepository).save(detalleCaptor.capture());
        assertThat(detalleCaptor.getValue().getCantidadRecibida()).isEqualTo(65);
    }
}
