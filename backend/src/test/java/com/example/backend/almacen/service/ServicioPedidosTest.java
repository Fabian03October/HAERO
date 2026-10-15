package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.PedidoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Pedido;
import com.example.backend.almacen.entity.PedidoDetalle;
import com.example.backend.almacen.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * HU22 y HU11: los pedidos se leen de Intelisis (solo consulta). Se muestran con su
 * contrato y estatus de Intelisis, y lo que Almacén recibió en HAERO por separado.
 */
@ExtendWith(MockitoExtension.class)
class ServicioPedidosTest {

    @Mock
    private PedidoRepository pedidoRepository;

    private ServicioPedidos servicioPedidos;

    @BeforeEach
    void configurar() {
        servicioPedidos = new ServicioPedidos(pedidoRepository);
    }

    private Pedido pedidoDeIntelisis() {
        Medicamento medicamento = new Medicamento();
        medicamento.setClave("010.000.0104.00");
        medicamento.setNombreGenerico("PARACETAMOL");

        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumeroPedido("PP-8632");
        pedido.setProveedor("Glenmark Pharmaceuticals");
        pedido.setEstatus("ACTIVO");
        pedido.setFecha(LocalDate.of(2026, 10, 12));
        pedido.setOrigen("INTELISIS");
        pedido.setContrato("IB-HRAEO-AD-B-C-310-2025");
        pedido.setEstatusIntelisis("PENDIENTE");

        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setPedido(pedido);
        detalle.setMedicamento(medicamento);
        detalle.setCantidadEsperada(56);
        detalle.setCantidadRecibida(20);
        detalle.setCucop("25301169");
        detalle.setRecibidaIntelisis(0);
        detalle.setCanceladaIntelisis(0);
        pedido.getPartidas().add(detalle);
        return pedido;
    }

    @Test
    void listar_devuelveElPedidoConSuContratoYLoRecibidoEnHaeroAparte() {
        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoDeIntelisis()));

        PedidoResponse respuesta = servicioPedidos.listarPedidos(null).get(0);

        assertThat(respuesta.getContrato()).isEqualTo("IB-HRAEO-AD-B-C-310-2025");
        assertThat(respuesta.getEstatusIntelisis()).isEqualTo("PENDIENTE");
        assertThat(respuesta.getPartidas().get(0).getCantidadRecibida()).isEqualTo(20);
        assertThat(respuesta.getPartidas().get(0).getRecibidaIntelisis()).isZero();
        assertThat(respuesta.getPartidas().get(0).getCucop()).isEqualTo("25301169");
    }

    @Test
    void listar_porEstatus_usaElFiltro() {
        when(pedidoRepository.findByEstatus("CANCELADO")).thenReturn(List.of());

        assertThat(servicioPedidos.listarPedidos("CANCELADO")).isEmpty();
    }

    @Test
    void obtener_conIdInexistente_lanza404() {
        when(pedidoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioPedidos.obtenerPedido(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Pedido no encontrado");
    }
}
