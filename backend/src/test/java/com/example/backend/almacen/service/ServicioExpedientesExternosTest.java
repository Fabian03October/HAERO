package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.DevolucionPrestamoRequest;
import com.example.backend.almacen.dto.ExpedienteExternoResponse;
import com.example.backend.almacen.dto.MovimientoExternoRequest;
import com.example.backend.almacen.dto.RegistrarExpedienteRequest;
import com.example.backend.almacen.entity.DocumentoExterno;
import com.example.backend.almacen.entity.Existencia;
import com.example.backend.almacen.entity.ExpedienteExterno;
import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.almacen.repository.DocumentoExternoRepository;
import com.example.backend.almacen.repository.ExpedienteExternoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
 * Préstamos y transferencias con expediente y documentos PDF (ajuste HU20).
 */
@ExtendWith(MockitoExtension.class)
class ServicioExpedientesExternosTest {

    private static final byte[] PDF = "%PDF-1.7 contenido de prueba".getBytes();

    @Mock
    private ExpedienteExternoRepository expedienteRepository;

    @Mock
    private DocumentoExternoRepository documentoRepository;

    @Mock
    private MovimientoRepository movimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ServicioMovimientosEspeciales movimientosEspeciales;

    private ServicioExpedientesExternos servicio;

    private final Usuario usuario = new Usuario();
    private final Medicamento medicamento = new Medicamento();

    @BeforeEach
    void configurar() {
        servicio = new ServicioExpedientesExternos(expedienteRepository, documentoRepository, movimientoRepository,
                usuarioRepository, movimientosEspeciales, new ServicioMovimientos(movimientoRepository));
        usuario.setNombreUsuario("almacen");
        usuario.setNombreCompleto("Encargada de Almacén");
        medicamento.setClave("010.000.6059.01-1");
        medicamento.setNombreGenerico("Lactulosa");
        lenient().when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        lenient().when(expedienteRepository.save(any(ExpedienteExterno.class))).thenAnswer(invocacion -> {
            ExpedienteExterno expediente = invocacion.getArgument(0);
            if (expediente.getId() == null) {
                expediente.setId(7L);
            }
            return expediente;
        });
        lenient().when(movimientosEspeciales.aplicarExterno(any(), any(), any())).thenAnswer(invocacion -> movimiento());
    }

    // ---------- Alta ----------

    @Test
    void registrarPrestamo_sinDocumento_lanza400YNoMueveInventario() {
        assertThatThrownBy(() -> servicio.registrar(prestamo(LocalDate.now().plusDays(30)), null, "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("oficio de solicitud");

        verify(movimientosEspeciales, never()).aplicarExterno(any(), any(), any());
        verify(expedienteRepository, never()).save(any());
    }

    @Test
    void registrarPrestamo_sinFechaLimite_lanza400() {
        assertThatThrownBy(() -> servicio.registrar(prestamo(null), pdf("solicitud.pdf"), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fecha límite");
    }

    @Test
    void registrar_conArchivoQueNoEsPdf_lanza400() {
        MockMultipartFile imagen = new MockMultipartFile("documento", "oficio.pdf", "application/pdf", new byte[]{1, 2, 3, 4, 5});

        assertThatThrownBy(() -> servicio.registrar(prestamo(LocalDate.now().plusDays(30)), imagen, "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("debe ser un PDF");
    }

    @Test
    void registrarPrestamo_conDocumento_quedaActivoConSuSolicitudYSuMovimiento() {
        ExpedienteExternoResponse respuesta = servicio.registrar(prestamo(LocalDate.now().plusDays(30)), pdf("solicitud.pdf"), "almacen");

        assertThat(respuesta.getTipo()).isEqualTo("PRESTAMO");
        assertThat(respuesta.getEstatus()).isEqualTo("ACTIVO");
        assertThat(respuesta.getEstatusVigente()).isEqualTo("ACTIVO");
        assertThat(respuesta.getClave()).isEqualTo("010.000.6059.01-1");
        ArgumentCaptor<DocumentoExterno> documento = ArgumentCaptor.forClass(DocumentoExterno.class);
        verify(documentoRepository).save(documento.capture());
        assertThat(documento.getValue().getTipo()).isEqualTo("SOLICITUD");
        assertThat(documento.getValue().getNombreArchivo()).isEqualTo("solicitud.pdf");
        ArgumentCaptor<Movimiento> movimiento = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertThat(movimiento.getValue().getExpediente().getId()).isEqualTo(7L);
    }

    @Test
    void registrarTransferencia_noPideFechaLimiteYQuedaRegistrada() {
        RegistrarExpedienteRequest datos = prestamo(null);
        datos.setTipo("TRANSFERENCIA");

        ExpedienteExternoResponse respuesta = servicio.registrar(datos, pdf("envio.pdf"), "almacen");

        assertThat(respuesta.getEstatus()).isEqualTo("REGISTRADA");
        assertThat(respuesta.getFechaLimite()).isNull();
    }

    // ---------- Devoluciones ----------

    @Test
    void devolucion_mayorALoPendiente_lanza400() {
        ExpedienteExterno expediente = expediente("PRESTAMO", "ACTIVO", 20, 15);
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente));

        assertThatThrownBy(() -> servicio.registrarDevolucion(7L, devolucion(10), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo faltan 5 cajas");
    }

    @Test
    void devolucionParcial_entraEnSentidoContrarioYQuedaDevueltoParcial() {
        ExpedienteExterno expediente = expediente("PRESTAMO", "ACTIVO", 20, 0);
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente));

        ExpedienteExternoResponse respuesta = servicio.registrarDevolucion(7L, devolucion(8), "almacen");

        assertThat(respuesta.getCajasDevueltas()).isEqualTo(8);
        assertThat(respuesta.getEstatus()).isEqualTo("DEVUELTO_PARCIAL");
        ArgumentCaptor<MovimientoExternoRequest> movimiento = ArgumentCaptor.forClass(MovimientoExternoRequest.class);
        verify(movimientosEspeciales).aplicarExterno(movimiento.capture(), any(), eq(expediente));
        assertThat(movimiento.getValue().getSentido()).isEqualTo("ENTRADA");
        assertThat(movimiento.getValue().getClave()).isEqualTo("010.000.6059.01-1");
    }

    @Test
    void devolucion_deUnaTransferencia_lanza400() {
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente("TRANSFERENCIA", "REGISTRADA", 20, 0)));

        assertThatThrownBy(() -> servicio.registrarDevolucion(7L, devolucion(5), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo los préstamos");
    }

    // ---------- Cierre ----------

    @Test
    void cerrarPrestamo_conCajasPendientes_lanza400() {
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente("PRESTAMO", "DEVUELTO_PARCIAL", 20, 12)));

        assertThatThrownBy(() -> servicio.cerrar(7L, pdf("cierre.pdf"), "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("faltan 8 cajas");
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void cerrarPrestamo_devueltoCompleto_sinDocumento_lanza400() {
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente("PRESTAMO", "DEVUELTO_PARCIAL", 20, 20)));

        assertThatThrownBy(() -> servicio.cerrar(7L, null, "almacen"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("documento de cierre");
    }

    @Test
    void cerrarPrestamo_devueltoCompleto_quedaCerradoConSuDocumento() {
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente("PRESTAMO", "DEVUELTO_PARCIAL", 20, 20)));

        ExpedienteExternoResponse respuesta = servicio.cerrar(7L, pdf("cierre.pdf"), "almacen");

        assertThat(respuesta.getEstatus()).isEqualTo("CERRADO");
        assertThat(respuesta.getFechaCierre()).isNotNull();
        ArgumentCaptor<DocumentoExterno> documento = ArgumentCaptor.forClass(DocumentoExterno.class);
        verify(documentoRepository).save(documento.capture());
        assertThat(documento.getValue().getTipo()).isEqualTo("CIERRE");
    }

    @Test
    void confirmarTransferencia_quedaConfirmadaConDocumentoDeRecepcion() {
        when(expedienteRepository.findById(7L)).thenReturn(Optional.of(expediente("TRANSFERENCIA", "REGISTRADA", 20, 0)));

        ExpedienteExternoResponse respuesta = servicio.cerrar(7L, pdf("recepcion.pdf"), "almacen");

        assertThat(respuesta.getEstatus()).isEqualTo("CONFIRMADA");
        ArgumentCaptor<DocumentoExterno> documento = ArgumentCaptor.forClass(DocumentoExterno.class);
        verify(documentoRepository).save(documento.capture());
        assertThat(documento.getValue().getTipo()).isEqualTo("RECEPCION");
    }

    // ---------- Estatus calculado ----------

    @Test
    void estatusVigente_prestamoConFechaLimitePasada_esVencido() {
        ExpedienteExterno expediente = expediente("PRESTAMO", "ACTIVO", 20, 5);
        expediente.setFechaLimite(LocalDate.of(2026, 9, 30));

        assertThat(ServicioExpedientesExternos.estatusVigente(expediente, LocalDate.of(2026, 10, 6))).isEqualTo("VENCIDO");
    }

    @Test
    void estatusVigente_todoDevueltoSinCerrar_esPorCerrar() {
        ExpedienteExterno expediente = expediente("PRESTAMO", "DEVUELTO_PARCIAL", 20, 20);
        expediente.setFechaLimite(LocalDate.of(2026, 9, 30));

        assertThat(ServicioExpedientesExternos.estatusVigente(expediente, LocalDate.of(2026, 10, 6))).isEqualTo("POR_CERRAR");
    }

    @Test
    void estatusVigente_prestamoCerrado_noSeVence() {
        ExpedienteExterno expediente = expediente("PRESTAMO", "CERRADO", 20, 20);
        expediente.setFechaLimite(LocalDate.of(2026, 9, 30));

        assertThat(ServicioExpedientesExternos.estatusVigente(expediente, LocalDate.of(2026, 10, 6))).isEqualTo("CERRADO");
    }

    // ---------- helpers ----------

    private RegistrarExpedienteRequest prestamo(LocalDate fechaLimite) {
        RegistrarExpedienteRequest datos = new RegistrarExpedienteRequest();
        datos.setTipo("PRESTAMO");
        datos.setSentido("SALIDA");
        datos.setInstitucion("Hospital General");
        datos.setCajas(20);
        datos.setExistenciaId(40L);
        datos.setFechaLimite(fechaLimite);
        return datos;
    }

    private DevolucionPrestamoRequest devolucion(int cajas) {
        DevolucionPrestamoRequest request = new DevolucionPrestamoRequest();
        request.setCajas(cajas);
        request.setNumeroLote("L-DEV");
        request.setCaducidad(LocalDate.now().plusYears(1));
        request.setUbicacion("SECTOR 2");
        return request;
    }

    private ExpedienteExterno expediente(String tipo, String estatus, int cajas, int devueltas) {
        ExpedienteExterno expediente = new ExpedienteExterno();
        expediente.setId(7L);
        expediente.setTipo(tipo);
        expediente.setSentido("SALIDA");
        expediente.setInstitucion("Hospital General");
        expediente.setMedicamento(medicamento);
        expediente.setCajas(cajas);
        expediente.setCajasDevueltas(devueltas);
        expediente.setEstatus(estatus);
        expediente.setUsuario(usuario);
        return expediente;
    }

    private Movimiento movimiento() {
        Lote lote = new Lote();
        lote.setMedicamento(medicamento);
        lote.setNumeroLote("L-1");
        lote.setCaducidad(LocalDate.now().plusYears(1));
        Existencia existencia = new Existencia();
        existencia.setLote(lote);
        existencia.setUbicacion("SECTOR 1");
        Movimiento movimiento = new Movimiento();
        movimiento.setExistencia(existencia);
        movimiento.setTipo("PRESTAMO");
        movimiento.setCantidadCajas(20);
        movimiento.setUsuario(usuario);
        return movimiento;
    }

    private static MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("documento", nombre, "application/pdf", PDF);
    }
}
