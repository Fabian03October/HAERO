package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
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
import static org.mockito.Mockito.when;

/**
 * Catálogo de medicamentos: solo consulta (los datos vienen de Intelisis) y si cada
 * clave ya está dada de alta en HAERO (tiene código de barras).
 */
@ExtendWith(MockitoExtension.class)
class ServicioMedicamentosTest {

    @Mock
    private MedicamentoRepository medicamentoRepository;
    @Mock
    private CodigoBarrasRepository codigoBarrasRepository;

    private ServicioMedicamentos servicioMedicamentos;

    @BeforeEach
    void configurar() {
        servicioMedicamentos = new ServicioMedicamentos(medicamentoRepository, codigoBarrasRepository);
    }

    private Medicamento medicamento(String clave, String nombre) {
        Medicamento medicamento = new Medicamento();
        medicamento.setClave(clave);
        medicamento.setNombreGenerico(nombre);
        medicamento.setPresentacion("Caja");
        return medicamento;
    }

    @Test
    void obtener_conClaveExistente_devuelveElMedicamento() {
        when(medicamentoRepository.findById("0134")).thenReturn(Optional.of(medicamento("0134", "Paracetamol 500 mg")));

        MedicamentoResponse respuesta = servicioMedicamentos.obtener("0134");

        assertThat(respuesta.getNombreGenerico()).isEqualTo("Paracetamol 500 mg");
        assertThat(respuesta.isDadoDeAlta()).isFalse();
    }

    @Test
    void obtener_conClaveInexistente_lanza404() {
        when(medicamentoRepository.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioMedicamentos.obtener("9999"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Medicamento no encontrado");
    }

    @Test
    void listar_devuelveTodosLosMedicamentos() {
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento("0134", "Paracetamol"), medicamento("0210", "Insulina")));

        List<MedicamentoResponse> respuesta = servicioMedicamentos.listar();

        assertThat(respuesta).extracting(MedicamentoResponse::getClave).containsExactly("0134", "0210");
    }

    @Test
    void listar_marcaComoDadaDeAltaSoloLaClaveQueTieneCodigoDeBarras() {
        when(medicamentoRepository.findAll()).thenReturn(List.of(medicamento("0134", "Paracetamol"), medicamento("0210", "Insulina")));
        when(codigoBarrasRepository.contarPorClave()).thenReturn(List.<Object[]>of(new Object[]{"0134", 2L}));

        List<MedicamentoResponse> respuesta = servicioMedicamentos.listar();

        assertThat(respuesta.get(0).isDadoDeAlta()).isTrue();
        assertThat(respuesta.get(0).getCodigosBarras()).isEqualTo(2);
        assertThat(respuesta.get(1).isDadoDeAlta()).isFalse();
    }
}
