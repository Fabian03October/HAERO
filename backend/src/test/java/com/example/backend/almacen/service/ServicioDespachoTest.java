package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.DespacharSolicitudRequest;
import com.example.backend.almacen.dto.DespacharSolicitudResponse;
import com.example.backend.almacen.dto.PartidaDespacho;
import com.example.backend.almacen.dto.SugerenciaDespachoResponse;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.almacen.repository.SolicitudRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PU-06 a PU-10 (HU14, HU15): validacion y despacho de solicitud, bloqueo FEFO.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDespachoTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private LoteRepository loteRepository;
    @Mock
    private ExistenciaRepository existenciaRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private SolicitudRepository solicitudRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private com.example.backend.almacen.repository.SolicitudPedidoRepository solicitudPedidoRepository;
    @Mock
    private CodigoBarrasRepository codigoBarrasRepository;

    private ServicioDespacho servicioDespacho;

    private Medicamento medicamento;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicioDespacho = new ServicioDespacho(medicamentoRepository, loteRepository, existenciaRepository,
                movimientoRepository, solicitudRepository, usuarioRepository, codigoBarrasRepository, solicitudPedidoRepository);

        medicamento = new Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");

        usuario = new Usuario();
        usuario.setNombreUsuario("despachador1");

        lenient().when(usuarioRepository.findByNombreUsuario("despachador1")).thenReturn(Optional.of(usuario));
    }

    @Test
    void listarExistencias_marcaComoFefoElLoteVigenteQueCaducaAntes_aunqueSeFiltrePorUbicacion() {
        Lote vencido = lote(1L, "L-VENCIDO", LocalDate.now().minusDays(3));
        Lote pronto = lote(2L, "L-PRONTO", LocalDate.now().plusMonths(2));
        Lote despues = lote(3L, "L-DESPUES", LocalDate.now().plusMonths(8));
        Lote agotado = lote(4L, "L-AGOTADO", LocalDate.now().plusMonths(1));
        when(existenciaRepository.findAllConLote()).thenReturn(List.of(
                existencia(10L, vencido, "Sector 1", 5),
                existencia(11L, pronto, "Sector 2", 8),
                existencia(12L, despues, "Sector 1", 20),
                existencia(13L, agotado, "Sector 1", 0)));

        var enSector1 = servicioDespacho.listarExistencias(null, null, "Sector 1");

        // El FEFO es L-PRONTO (Sector 2) aunque no esté en el filtro: nada del Sector 1 sale primero.
        assertThat(enSector1).hasSize(3).noneMatch(e -> e.isFefo());
        assertThat(servicioDespacho.listarExistencias(null, null, null))
                .filteredOn(e -> e.isFefo()).extracting(e -> e.getNumeroLote()).containsExactly("L-PRONTO");
    }

    private Solicitud solicitudPendiente(int cantidadSolicitada) {
        Solicitud solicitud = new Solicitud();
        solicitud.setId(1L);
        solicitud.setMedicamento(medicamento);
        solicitud.setCantidadSolicitada(cantidadSolicitada);
        solicitud.setCantidadAtendida(0);
        solicitud.setEstatus("PENDIENTE");
        return solicitud;
    }

    private Lote lote(Long id, String numero, LocalDate caducidad) {
        Lote lote = new Lote();
        lote.setId(id);
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(numero);
        lote.setCaducidad(caducidad);
        lote.setEstatus("DISPONIBLE");
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

    // ---------- Farmacia ve las solicitudes de toda el área ----------

    @Test
    void listarSolicitudesDeFarmacia_incluyeLasDeTodosLosUsuariosConQuienLasHizo() {
        Usuario fabian = new Usuario();
        fabian.setNombreUsuario("fabiancito");
        fabian.setNombreCompleto("Fabián de Jesús Jiménez Castillejos");
        Usuario rubi = new Usuario();
        rubi.setNombreUsuario("rubi");
        rubi.setNombreCompleto("Rubí Morales");
        Solicitud deFabian = solicitudPendiente(10);
        deFabian.setUsuario(fabian);
        Solicitud deRubi = solicitudPendiente(5);
        deRubi.setId(2L);
        deRubi.setUsuario(rubi);
        when(solicitudRepository.findAllByOrderByFechaDesc()).thenReturn(List.of(deRubi, deFabian));

        var lista = servicioDespacho.listarSolicitudesDeFarmacia();

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).getSolicitadoPor()).isEqualTo("Rubí Morales");
        assertThat(lista.get(0).getSolicitadoPorUsuario()).isEqualTo("rubi");
        assertThat(lista.get(1).getSolicitadoPor()).isEqualTo("Fabián de Jesús Jiménez Castillejos");
    }

    // ---------- PU-06: partida mayor a la existencia ----------

    @Test
    void despachar_conPartidaMayorALaExistencia_lanza400YNoModificaNada() {
        Solicitud solicitud = solicitudPendiente(20);
        Lote lote = lote(1L, "L-8821", LocalDate.of(2028, 7, 1));
        Existencia existencia = existencia(10L, lote, "Sector 5", 15);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existencia));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("excede la existencia disponible");

        verify(existenciaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    // ---------- PU-07: bloqueo FEFO ----------

    @Test
    void despachar_conLoteDeCaducidadMasLejana_habiendoUnoMasProximo_lanza409YNadaDescontado() {
        Solicitud solicitud = solicitudPendiente(20);
        Lote loteProximo = lote(1L, "L-7940", LocalDate.of(2027, 2, 1));
        Lote loteLejano = lote(2L, "L-8821", LocalDate.of(2028, 7, 1));
        Existencia existenciaProxima = existencia(10L, loteProximo, "Sector 3", 10);
        Existencia existenciaLejana = existencia(20L, loteLejano, "Sector 5", 40);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(20L)).thenReturn(Optional.of(existenciaLejana));
        when(loteRepository.findByMedicamentoClaveAndEstatus("0134", "DISPONIBLE"))
                .thenReturn(List.of(loteProximo, loteLejano));
        when(existenciaRepository.findByLoteId(1L)).thenReturn(List.of(existenciaProxima));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(20L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("caducidad más próxima");

        verify(existenciaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    // ---------- PU-08: dos lotes con la misma caducidad ----------

    @Test
    void despachar_conDosLotesMismaCaducidad_permiteCualquieraDeLosDos() {
        Solicitud solicitud = solicitudPendiente(5);
        LocalDate mismaCaducidad = LocalDate.of(2027, 6, 1);
        Lote loteA = lote(1L, "L-A", mismaCaducidad);
        Lote loteB = lote(2L, "L-B", mismaCaducidad);
        Existencia existenciaA = existencia(10L, loteA, "Sector A", 10);
        Existencia existenciaB = existencia(20L, loteB, "Sector B", 10);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(20L)).thenReturn(Optional.of(existenciaB));
        when(loteRepository.findByMedicamentoClaveAndEstatus("0134", "DISPONIBLE"))
                .thenReturn(List.of(loteA, loteB));
        when(existenciaRepository.save(any(Existencia.class))).thenAnswer(inv -> inv.getArgument(0));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(20L); // elige el lote B, no el "primero" en la lista
        partida.setCajas(5);
        request.setPartidas(List.of(partida));

        DespacharSolicitudResponse respuesta = servicioDespacho.despachar(1L, request, "despachador1");

        assertThat(respuesta.getEstatus()).isEqualTo("ATENDIDA");
        verify(existenciaRepository).save(any(Existencia.class));
        verify(movimientoRepository).save(any());
    }

    // ---------- PU-09: lote vencido aun marcado DISPONIBLE ----------

    @Test
    void sugerir_conLoteVencidoDisponible_noApareceEnLaSugerencia() {
        Solicitud solicitud = solicitudPendiente(10);
        Lote loteVencido = lote(1L, "L-VIEJO", LocalDate.now().minusDays(5));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(loteRepository.findByMedicamentoClaveAndEstatus("0134", "DISPONIBLE")).thenReturn(List.of(loteVencido));

        SugerenciaDespachoResponse sugerencia = servicioDespacho.sugerir(1L);

        assertThat(sugerencia.getPartidas()).isEmpty();
    }

    @Test
    void despachar_conLoteVencidoAunDisponible_lanza400YNoDescuenta() {
        Solicitud solicitud = solicitudPendiente(10);
        Lote loteVencido = lote(1L, "L-VIEJO", LocalDate.now().minusDays(5));
        Existencia existenciaVencida = existencia(10L, loteVencido, "Sector 9", 50);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existenciaVencida));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(5);
        request.setPartidas(List.of(partida));

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vencido");

        verify(existenciaRepository, never()).save(any());
    }

    // ---------- PU-10: entrega parcial ----------

    @Test
    void despachar_conCantidadMenorALaPendienteYParcialConfirmado_dejaSolicitudEnPARCIAL() {
        Solicitud solicitud = solicitudPendiente(100);
        Lote lote = lote(1L, "L-8821", LocalDate.of(2028, 7, 1));
        Existencia existencia = existencia(10L, lote, "Sector 5", 60);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existencia));
        when(loteRepository.findByMedicamentoClaveAndEstatus("0134", "DISPONIBLE")).thenReturn(List.of(lote));
        when(existenciaRepository.save(any(Existencia.class))).thenAnswer(inv -> inv.getArgument(0));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(60);
        request.setPartidas(List.of(partida));
        request.setParcial(true);

        DespacharSolicitudResponse respuesta = servicioDespacho.despachar(1L, request, "despachador1");

        assertThat(respuesta.getEstatus()).isEqualTo("PARCIAL");
        assertThat(respuesta.getCantidadAtendida()).isEqualTo(60);
        assertThat(respuesta.getCantidadSolicitada()).isEqualTo(100);
    }

    @Test
    void despachar_conCantidadMenorALaPendienteSinConfirmarParcial_lanza400() {
        Solicitud solicitud = solicitudPendiente(100);
        Lote lote = lote(1L, "L-8821", LocalDate.of(2028, 7, 1));
        Existencia existencia = existencia(10L, lote, "Sector 5", 60);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existencia));
        when(loteRepository.findByMedicamentoClaveAndEstatus("0134", "DISPONIBLE")).thenReturn(List.of(lote));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(60);
        request.setPartidas(List.of(partida));
        request.setParcial(false);

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no cubre lo solicitado");

        verify(existenciaRepository, never()).save(any());
    }

    // ---------- Ajuste HU15: confirmarFueraDeFefo ----------

    @Test
    void despachar_conLoteLejanoYConfirmarFueraDeFefo_permiteElDespacho() {
        Solicitud solicitud = solicitudPendiente(20);
        Lote loteProximo = lote(1L, "L-7940", LocalDate.of(2027, 2, 1));
        Lote loteLejano = lote(2L, "L-8821", LocalDate.of(2028, 7, 1));
        Existencia existenciaLejana = existencia(20L, loteLejano, "Sector 5", 40);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(20L)).thenReturn(Optional.of(existenciaLejana));
        when(loteRepository.findByMedicamentoClaveAndEstatus(any(), any())).thenReturn(List.of(loteProximo, loteLejano));
        when(existenciaRepository.findByLoteId(1L)).thenReturn(List.of(existencia(10L, loteProximo, "Sector 1", 5)));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(20L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));
        request.setConfirmarFueraDeFefo(true);
        request.setMotivoFueraDeFefo("El lote próximo está en cuarentena");

        DespacharSolicitudResponse respuesta = servicioDespacho.despachar(1L, request, "despachador1");

        assertThat(respuesta.getEstatus()).isEqualTo("ATENDIDA");
        verify(existenciaRepository).save(existenciaLejana);
        org.mockito.ArgumentCaptor<com.example.backend.almacen.entity.Movimiento> captor =
                org.mockito.ArgumentCaptor.forClass(com.example.backend.almacen.entity.Movimiento.class);
        verify(movimientoRepository).save(captor.capture());
        assertThat(captor.getValue().getMotivo()).isEqualTo("Fuera de FEFO: El lote próximo está en cuarentena");
    }

    @Test
    void despachar_fueraDeFefoSinMotivo_lanza400() {
        // HU15 CA2: salirse del orden FEFO solo con motivo, para que quede registrado.
        Solicitud solicitud = solicitudPendiente(20);
        Lote loteProximo = lote(1L, "L-7940", LocalDate.of(2027, 2, 1));
        Lote loteLejano = lote(2L, "L-8821", LocalDate.of(2028, 7, 1));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(20L)).thenReturn(Optional.of(existencia(20L, loteLejano, "Sector 5", 40)));
        when(loteRepository.findByMedicamentoClaveAndEstatus(any(), any())).thenReturn(List.of(loteProximo, loteLejano));
        when(existenciaRepository.findByLoteId(1L)).thenReturn(List.of(existencia(10L, loteProximo, "Sector 1", 5)));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(20L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));
        request.setConfirmarFueraDeFefo(true);

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("por qué");
        verify(existenciaRepository, never()).save(any());
    }

    @Test
    void despachar_conLoteVencidoAunConfirmarFueraDeFefo_siguelanzando400() {
        // El bloqueo de vencido NUNCA se salta, ni confirmando fuera de FEFO:
        // no es una preferencia de orden, es que no se dispensa lo caducado.
        Solicitud solicitud = solicitudPendiente(20);
        Lote loteVencido = lote(1L, "L-0001", LocalDate.now().minusDays(1));
        Existencia existencia = existencia(10L, loteVencido, "Sector 5", 40);

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existencia));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));
        request.setConfirmarFueraDeFefo(true);

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vencido");

        verify(existenciaRepository, never()).save(any());
    }

    // ---------- HU40: cancelar y rechazar ----------

    @Test
    void rechazar_conservaLoDespachadoYGuardaQuienYPorQue() {
        Solicitud solicitud = solicitudPendiente(20);
        solicitud.setCantidadAtendida(5);
        solicitud.setEstatus("PARCIAL");
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicioDespacho.rechazar(1L, "Sin existencia en Almacén", "despachador1");

        assertThat(respuesta.getEstatus()).isEqualTo("RECHAZADA");
        assertThat(respuesta.getCantidadAtendida()).isEqualTo(5);
        assertThat(respuesta.getMotivoCierre()).isEqualTo("Sin existencia en Almacén");
        assertThat(solicitud.getCerradaPor()).isNotNull();
        assertThat(solicitud.getFechaCierre()).isNotNull();
    }

    @Test
    void rechazar_sinMotivo_lanza400() {
        assertThatThrownBy(() -> servicioDespacho.rechazar(1L, "  ", "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("por qué");
    }

    @Test
    void cancelar_unaSolicitudYaAtendida_noSePuede() {
        Solicitud solicitud = solicitudPendiente(20);
        solicitud.setEstatus("ATENDIDA");
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThatThrownBy(() -> servicioDespacho.cancelar(1L, null, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("atendida");
    }

    @Test
    void despachar_unaSolicitudCancelada_noSePuede() {
        Solicitud solicitud = solicitudPendiente(20);
        solicitud.setEstatus("CANCELADA");
        solicitud.setMotivoCierre("Farmacia ya no lo necesita");
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(10L);
        partida.setCajas(1);
        request.setPartidas(List.of(partida));

        assertThatThrownBy(() -> servicioDespacho.despachar(1L, request, "despachador1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelada");
    }

    // ---------- HU39: pedido con varias claves ----------

    private com.example.backend.almacen.dto.CrearSolicitudRequest partidaPedido(String clave, Integer cantidad) {
        com.example.backend.almacen.dto.CrearSolicitudRequest partida = new com.example.backend.almacen.dto.CrearSolicitudRequest();
        partida.setClave(clave);
        partida.setCantidad(cantidad);
        return partida;
    }

    private com.example.backend.almacen.dto.CrearPedidoRequest pedido(com.example.backend.almacen.dto.CrearSolicitudRequest... partidas) {
        com.example.backend.almacen.dto.CrearPedidoRequest request = new com.example.backend.almacen.dto.CrearPedidoRequest();
        request.setPartidas(List.of(partidas));
        return request;
    }

    @Test
    void crearPedido_guardaUnaSolicitudPorClaveLigadasAlMismoPedido() {
        Medicamento otro = new Medicamento();
        otro.setClave("010.000.0104.00");
        otro.setNombreGenerico("Paracetamol");
        when(medicamentoRepository.findById(medicamento.getClave())).thenReturn(Optional.of(medicamento));
        when(medicamentoRepository.findById("010.000.0104.00")).thenReturn(Optional.of(otro));
        when(codigoBarrasRepository.existsByMedicamentoClave(any())).thenReturn(true);
        when(solicitudPedidoRepository.save(any())).thenAnswer(inv -> {
            com.example.backend.almacen.entity.SolicitudPedido p = inv.getArgument(0);
            p.setId(12L);
            return p;
        });
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        var creadas = servicioDespacho.crearPedido(pedido(partidaPedido(medicamento.getClave(), 4), partidaPedido("010.000.0104.00", 2)), "despachador1");

        assertThat(creadas).hasSize(2);
        assertThat(creadas).allSatisfy(s -> assertThat(s.getPedidoId()).isEqualTo(12L));
        assertThat(creadas).extracting(com.example.backend.almacen.dto.SolicitudResponse::getEstatus).containsOnly("PENDIENTE");
    }

    @Test
    void crearPedido_conUnaClaveMala_noGuardaNada() {
        when(medicamentoRepository.findById(medicamento.getClave())).thenReturn(Optional.of(medicamento));
        when(medicamentoRepository.findById("999")).thenReturn(Optional.empty());
        when(codigoBarrasRepository.existsByMedicamentoClave(any())).thenReturn(true);

        assertThatThrownBy(() -> servicioDespacho.crearPedido(pedido(partidaPedido(medicamento.getClave(), 4), partidaPedido("999", 2)), "despachador1"))
                .hasMessageContaining("999");
        verify(solicitudPedidoRepository, never()).save(any());
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void crearPedido_conLaMismaClaveDosVeces_seRechaza() {
        assertThatThrownBy(() -> servicioDespacho.crearPedido(pedido(partidaPedido("0134", 1), partidaPedido("0134", 2)), "despachador1"))
                .hasMessageContaining("dos veces");
    }

    @Test
    void despacharPedido_sinExistenciaDeUnaClaveYSinParcial_noDespachaNinguna() {
        Solicitud solicitud = solicitudPendiente(5);
        solicitud.setId(7L);
        when(solicitudRepository.findByPedidoIdAndEstatusInOrderByIdAsc(eq(12L), any())).thenReturn(List.of(solicitud));
        when(solicitudRepository.findById(7L)).thenReturn(Optional.of(solicitud));
        when(loteRepository.findByMedicamentoClaveAndEstatus(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> servicioDespacho.despacharPedido(12L, false, "despachador1"))
                .hasMessageContaining("No hay existencia de " + medicamento.getClave());
        verify(existenciaRepository, never()).save(any());
    }

    @Test
    void despacharPedido_conParcial_dejaPendienteLaClaveSinExistencia() {
        Solicitud solicitud = solicitudPendiente(5);
        solicitud.setId(7L);
        when(solicitudRepository.findByPedidoIdAndEstatusInOrderByIdAsc(eq(12L), any())).thenReturn(List.of(solicitud));
        when(solicitudRepository.findById(7L)).thenReturn(Optional.of(solicitud));
        when(loteRepository.findByMedicamentoClaveAndEstatus(any(), any())).thenReturn(List.of());

        var respuesta = servicioDespacho.despacharPedido(12L, true, "despachador1");

        assertThat(respuesta.despachadas()).isEmpty();
        assertThat(respuesta.sinExistencia()).containsExactly(medicamento.getClave());
        assertThat(solicitud.getEstatus()).isEqualTo("PENDIENTE");
    }

    @Test
    void despacharPedido_despachaCadaClaveConSuSugerenciaFefo() {
        Solicitud solicitud = solicitudPendiente(5);
        solicitud.setId(7L);
        Lote proximo = lote(1L, "L-1", LocalDate.now().plusMonths(6));
        Existencia existencia = existencia(10L, proximo, "Sector 1", 8);
        when(solicitudRepository.findByPedidoIdAndEstatusInOrderByIdAsc(eq(12L), any())).thenReturn(List.of(solicitud));
        when(solicitudRepository.findById(7L)).thenReturn(Optional.of(solicitud));
        when(loteRepository.findByMedicamentoClaveAndEstatus(any(), any())).thenReturn(List.of(proximo));
        when(existenciaRepository.findByLoteId(1L)).thenReturn(List.of(existencia));
        when(existenciaRepository.findById(10L)).thenReturn(Optional.of(existencia));
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicioDespacho.despacharPedido(12L, false, "despachador1");

        assertThat(respuesta.despachadas()).hasSize(1);
        assertThat(solicitud.getEstatus()).isEqualTo("ATENDIDA");
        assertThat(existencia.getCantidadCajas()).isEqualTo(3);
    }
}
