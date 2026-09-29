package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CrearPedidoRequest;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.PedidoRepository;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PU-01 (HU22): registrar pedido con numero duplicado.
 */
@ExtendWith(MockitoExtension.class)
class ServicioPedidosTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private ServicioPedidos servicioPedidos;

    @BeforeEach
    void configurar() {
        servicioPedidos = new ServicioPedidos(pedidoRepository, medicamentoRepository, usuarioRepository);
    }

    @Test
    void crearPedido_conNumeroDuplicado_lanza400YNoLoGuarda() {
        CrearPedidoRequest request = new CrearPedidoRequest();
        request.setNumeroPedido("PED-2026-0148");

        when(pedidoRepository.existsByNumeroPedido("PED-2026-0148")).thenReturn(true);

        assertThatThrownBy(() -> servicioPedidos.crearPedido(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe un pedido con ese número");

        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crearPedido_sinPartidas_lanza400() {
        CrearPedidoRequest request = new CrearPedidoRequest();
        request.setNumeroPedido("PED-2026-0200");
        request.setPartidas(null);

        when(pedidoRepository.existsByNumeroPedido("PED-2026-0200")).thenReturn(false);

        assertThatThrownBy(() -> servicioPedidos.crearPedido(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("al menos una partida");

        verify(pedidoRepository, never()).save(any());
    }
}
