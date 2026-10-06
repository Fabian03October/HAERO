package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MovimientoResponse;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consulta de movimientos (HU14 CA5): filtro por tipo y rango de fechas.
 */
@ExtendWith(MockitoExtension.class)
class ServicioMovimientosTest {

    @Mock
    private MovimientoRepository movimientoRepository;

    private ServicioMovimientos servicioMovimientos;

    @BeforeEach
    void configurar() {
        servicioMovimientos = new ServicioMovimientos(movimientoRepository);
    }

    @Test
    void listar_filtraPorTipoYDevuelveLoteYUbicacion() {
        Movimiento salida = movimiento("SALIDA", 30, 7L);
        Movimiento entrada = movimiento("ENTRADA", 100, null);
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of(salida, entrada));

        List<MovimientoResponse> resultado = servicioMovimientos.listar("SALIDA", null, null);

        assertThat(resultado).hasSize(1);
        MovimientoResponse respuesta = resultado.get(0);
        assertThat(respuesta.getTipo()).isEqualTo("SALIDA");
        assertThat(respuesta.getClave()).isEqualTo("010.000.6059.01-1");
        assertThat(respuesta.getNumeroLote()).isEqualTo("89016MC");
        assertThat(respuesta.getUbicacion()).isEqualTo("SECTOR 4");
        assertThat(respuesta.getCajas()).isEqualTo(30);
        assertThat(respuesta.getSolicitudId()).isEqualTo(7L);
        assertThat(respuesta.getUsuario()).isEqualTo("Usuario Almacén");
        assertThat(respuesta.getSolicitadoPor()).isEqualTo("Usuario Farmacia");
        assertThat(respuesta.getFechaSolicitud()).isEqualTo(LocalDateTime.of(2026, 10, 5, 9, 12));
        assertThat(respuesta.getCantidadSolicitada()).isEqualTo(50);
    }

    @Test
    void listar_movimientoSinSolicitud_dejaVaciosLosDatosDeFarmacia() {
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any()))
                .thenReturn(List.of(movimiento("ENTRADA", 100, null)));

        MovimientoResponse respuesta = servicioMovimientos.listar(null, null, null).get(0);

        assertThat(respuesta.getSolicitudId()).isNull();
        assertThat(respuesta.getSolicitadoPor()).isNull();
        assertThat(respuesta.getFechaSolicitud()).isNull();
        assertThat(respuesta.getCantidadSolicitada()).isNull();
    }

    @Test
    void listar_conUnDia_buscaDesdeElInicioHastaElFinDeEseDia() {
        LocalDate dia = LocalDate.of(2026, 10, 1);
        when(movimientoRepository.findByFechaBetweenOrderByFechaDesc(any(), any())).thenReturn(List.of());

        servicioMovimientos.listar(null, dia, dia);

        verify(movimientoRepository).findByFechaBetweenOrderByFechaDesc(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 23, 59, 59, 999_999_999));
    }

    @Test
    void listar_conRangoInvertido_lanza400() {
        assertThatThrownBy(() -> servicioMovimientos.listar(null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fecha final");
    }

    private Movimiento movimiento(String tipo, int cajas, Long solicitudId) {
        Medicamento medicamento = new Medicamento();
        medicamento.setClave("010.000.6059.01-1");
        medicamento.setNombreGenerico("Lactulosa");

        Lote lote = new Lote();
        lote.setMedicamento(medicamento);
        lote.setNumeroLote("89016MC");
        lote.setCaducidad(LocalDate.of(2028, 7, 9));

        Existencia existencia = new Existencia();
        existencia.setLote(lote);
        existencia.setUbicacion("SECTOR 4");

        Usuario usuario = new Usuario();
        usuario.setNombreCompleto("Usuario Almacén");

        Movimiento movimiento = new Movimiento();
        movimiento.setTipo(tipo);
        movimiento.setCantidadCajas(cajas);
        movimiento.setExistencia(existencia);
        movimiento.setUsuario(usuario);
        if (solicitudId != null) {
            Solicitud solicitud = new Solicitud();
            solicitud.setId(solicitudId);
            Usuario farmacia = new Usuario();
            farmacia.setNombreCompleto("Usuario Farmacia");
            solicitud.setUsuario(farmacia);
            solicitud.setFecha(LocalDateTime.of(2026, 10, 5, 9, 12));
            solicitud.setCantidadSolicitada(50);
            movimiento.setSolicitud(solicitud);
        }
        return movimiento;
    }
}
