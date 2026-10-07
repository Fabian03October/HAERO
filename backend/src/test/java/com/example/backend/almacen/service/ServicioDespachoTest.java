package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.DespacharSolicitudRequest;
import com.example.backend.almacen.dto.DespacharSolicitudResponse;
import com.example.backend.almacen.dto.PartidaDespacho;
import com.example.backend.almacen.dto.SugerenciaDespachoResponse;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Solicitud;
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

    private ServicioDespacho servicioDespacho;

    private Medicamento medicamento;
    private Usuario usuario;

    @BeforeEach
    void configurar() {
        servicioDespacho = new ServicioDespacho(medicamentoRepository, loteRepository, existenciaRepository,
                movimientoRepository, solicitudRepository, usuarioRepository);

        medicamento = new Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");

        usuario = new Usuario();
        usuario.setNombreUsuario("despachador1");

        lenient().when(usuarioRepository.findByNombreUsuario("despachador1")).thenReturn(Optional.of(usuario));
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
        when(solicitudRepository.save(any(Solicitud.class))).thenAnswer(inv -> inv.getArgument(0));

        DespacharSolicitudRequest request = new DespacharSolicitudRequest();
        PartidaDespacho partida = new PartidaDespacho();
        partida.setExistenciaId(20L);
        partida.setCajas(20);
        request.setPartidas(List.of(partida));
        request.setConfirmarFueraDeFefo(true);

        DespacharSolicitudResponse respuesta = servicioDespacho.despachar(1L, request, "despachador1");

        assertThat(respuesta.getEstatus()).isEqualTo("ATENDIDA");
        verify(existenciaRepository).save(existenciaLejana);
        verify(loteRepository, never()).findByMedicamentoClaveAndEstatus(any(), any());
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
}
