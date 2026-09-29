package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CargaInicialResponse;
import com.example.backend.almacen.repository.ExistenciaRepository;
import com.example.backend.almacen.repository.LoteRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MovimientoRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PU-15 (HU21): archivo con una clave inexistente.
 */
@ExtendWith(MockitoExtension.class)
class ServicioCargaInicialTest {

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

    private ServicioCargaInicial servicioCargaInicial;

    @BeforeEach
    void configurar() {
        servicioCargaInicial = new ServicioCargaInicial(
                medicamentoRepository, loteRepository, existenciaRepository, movimientoRepository, usuarioRepository);

        when(usuarioRepository.findByNombreUsuario("encargada1"))
                .thenReturn(Optional.of(new Usuario()));
    }

    @Test
    void cargar_conClaveInexistente_marcaRenglonConErrorYNoImportaNada() {
        when(medicamentoRepository.findById("9999")).thenReturn(Optional.empty());

        List<String> lineas = List.of(
                "clave,numero_lote,caducidad,proveedor,ubicacion,cantidad_cajas",
                "9999,L-0001,2028-01-01,Lab Sanfer,Sector 1,10"
        );

        CargaInicialResponse respuesta = servicioCargaInicial.cargar(lineas, "encargada1");

        assertThat(respuesta.isExito()).isFalse();
        assertThat(respuesta.getErrores()).hasSize(1);
        assertThat(respuesta.getErrores().get(0).getFila()).isEqualTo(2);
        assertThat(respuesta.getErrores().get(0).getMensaje()).contains("9999");

        verify(loteRepository, never()).save(any());
        verify(existenciaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void cargar_conDosRenglonesValidos_importaAmbosYNoReportaErrores() {
        var medicamento = new com.example.backend.almacen.entity.Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));
        when(loteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(existenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<String> lineas = List.of(
                "clave,numero_lote,caducidad,proveedor,ubicacion,cantidad_cajas",
                "0134,L-0001,2028-01-01,Lab Sanfer,Sector 1,40",
                "0134,L-0002,2027-06-01,Lab Sanfer,Sector 2,25"
        );

        CargaInicialResponse respuesta = servicioCargaInicial.cargar(lineas, "encargada1");

        assertThat(respuesta.isExito()).isTrue();
        assertThat(respuesta.getErrores()).isEmpty();
        assertThat(respuesta.getLotesCreados()).isEqualTo(2);

        verify(loteRepository, org.mockito.Mockito.times(2)).save(any());
        verify(existenciaRepository, org.mockito.Mockito.times(2)).save(any());
        verify(movimientoRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
