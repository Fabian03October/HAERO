package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CrearMedicamentoRequest;
import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.MedicamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Catálogo de medicamentos (HU21/HU22): alta, consulta y listado.
 */
@ExtendWith(MockitoExtension.class)
class ServicioMedicamentosTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;

    private ServicioMedicamentos servicioMedicamentos;

    @BeforeEach
    void configurar() {
        servicioMedicamentos = new ServicioMedicamentos(medicamentoRepository);
    }

    @Test
    void crear_conClaveNueva_loGuardaYDevuelveLosDatos() {
        CrearMedicamentoRequest request = new CrearMedicamentoRequest();
        request.setClave("0134");
        request.setNombreGenerico("Paracetamol 500 mg");
        request.setPresentacion("Caja con 20 tabletas");
        request.setPiezasPorCaja(20);
        request.setDescripcion("Analgésico");

        when(medicamentoRepository.existsById("0134")).thenReturn(false);
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicamentoResponse respuesta = servicioMedicamentos.crear(request);

        assertThat(respuesta.getClave()).isEqualTo("0134");
        assertThat(respuesta.getNombreGenerico()).isEqualTo("Paracetamol 500 mg");
        assertThat(respuesta.getPresentacion()).isEqualTo("Caja con 20 tabletas");
        assertThat(respuesta.getPiezasPorCaja()).isEqualTo(20);
    }

    @Test
    void crear_conClaveYaExistente_lanza409YNoLoGuarda() {
        CrearMedicamentoRequest request = new CrearMedicamentoRequest();
        request.setClave("0134");

        when(medicamentoRepository.existsById("0134")).thenReturn(true);

        assertThatThrownBy(() -> servicioMedicamentos.crear(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe un medicamento con esa clave");

        verify(medicamentoRepository, never()).save(any());
    }

    @Test
    void obtener_conClaveExistente_devuelveElMedicamento() {
        Medicamento medicamento = new Medicamento();
        medicamento.setClave("0134");
        medicamento.setNombreGenerico("Paracetamol 500 mg");

        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento));

        MedicamentoResponse respuesta = servicioMedicamentos.obtener("0134");

        assertThat(respuesta.getNombreGenerico()).isEqualTo("Paracetamol 500 mg");
    }

    @Test
    void obtener_conClaveInexistente_lanza404() {
        when(medicamentoRepository.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioMedicamentos.obtener("9999"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Medicamento no encontrado");
    }

    @Test
    void listar_devuelveTodosLosMedicamentosRegistrados() {
        Medicamento a = new Medicamento();
        a.setClave("0134");
        Medicamento b = new Medicamento();
        b.setClave("0210");

        when(medicamentoRepository.findAll()).thenReturn(List.of(a, b));

        List<MedicamentoResponse> respuesta = servicioMedicamentos.listar();

        assertThat(respuesta).extracting(MedicamentoResponse::getClave).containsExactly("0134", "0210");
    }
}
