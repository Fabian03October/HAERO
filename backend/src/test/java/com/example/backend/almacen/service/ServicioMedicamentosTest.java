package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.MedicamentoSeguridad;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MedicamentoSeguridadRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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
    @Mock
    private MedicamentoSeguridadRepository seguridadRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private ServicioMedicamentos servicioMedicamentos;

    @BeforeEach
    void configurar() {
        servicioMedicamentos = new ServicioMedicamentos(medicamentoRepository, codigoBarrasRepository, seguridadRepository, usuarioRepository);
    }

    private MedicamentoSeguridad marca(String clave, Boolean altoRiesgo, boolean redFria) {
        MedicamentoSeguridad seguridad = new MedicamentoSeguridad();
        seguridad.setClave(clave);
        seguridad.setAltoRiesgo(altoRiesgo);
        seguridad.setRedFria(redFria);
        return seguridad;
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

    @Test
    void listar_sinMarcaDeFarmacia_usaElAltoRiesgoDeIntelisis() {
        Medicamento potasio = medicamento("0524", "Cloruro de potasio");
        potasio.setRiesgo("ALTO");
        when(medicamentoRepository.findAll()).thenReturn(List.of(potasio, medicamento("0104", "Paracetamol")));

        List<MedicamentoResponse> respuesta = servicioMedicamentos.listar();

        assertThat(respuesta.get(0).isAltoRiesgo()).isTrue();
        assertThat(respuesta.get(0).getAltoRiesgoMarcado()).isNull();
        assertThat(respuesta.get(1).isAltoRiesgo()).isFalse();
    }

    @Test
    void listar_laMarcaDeFarmaciaGanaALoQueDiceIntelisis() {
        Medicamento potasio = medicamento("0524", "Cloruro de potasio");
        potasio.setRiesgo("ALTO");
        when(medicamentoRepository.findAll()).thenReturn(List.of(potasio, medicamento("1770", "Vincristina")));
        when(seguridadRepository.findAll()).thenReturn(List.of(marca("0524", false, false), marca("1770", true, true)));

        List<MedicamentoResponse> respuesta = servicioMedicamentos.listar();

        assertThat(respuesta.get(0).isAltoRiesgo()).isFalse();
        assertThat(respuesta.get(0).isAltoRiesgoIntelisis()).isTrue();
        assertThat(respuesta.get(1).isAltoRiesgo()).isTrue();
        assertThat(respuesta.get(1).isRedFria()).isTrue();
    }

    @Test
    void actualizarSeguridad_guardaLaMarcaConElUsuario() {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("farmacia");
        when(medicamentoRepository.findById("1770")).thenReturn(Optional.of(medicamento("1770", "Vincristina")));
        when(usuarioRepository.findByNombreUsuario("farmacia")).thenReturn(Optional.of(usuario));
        when(seguridadRepository.findById("1770")).thenReturn(Optional.empty());

        MedicamentoResponse respuesta = servicioMedicamentos.actualizarSeguridad("1770", true, true, "farmacia");

        ArgumentCaptor<MedicamentoSeguridad> guardada = ArgumentCaptor.forClass(MedicamentoSeguridad.class);
        verify(seguridadRepository).save(guardada.capture());
        assertThat(guardada.getValue().getUsuario()).isSameAs(usuario);
        assertThat(guardada.getValue().getActualizadoEn()).isNotNull();
        assertThat(respuesta.isAltoRiesgo()).isTrue();
        assertThat(respuesta.isRedFria()).isTrue();
    }

    @Test
    void actualizarSeguridad_conAltoRiesgoNulo_vuelveALoQueDiceIntelisis() {
        Medicamento potasio = medicamento("0524", "Cloruro de potasio");
        potasio.setRiesgo("ALTO");
        when(medicamentoRepository.findById("0524")).thenReturn(Optional.of(potasio));
        when(usuarioRepository.findByNombreUsuario("farmacia")).thenReturn(Optional.of(new Usuario()));
        when(seguridadRepository.findById("0524")).thenReturn(Optional.of(marca("0524", false, false)));

        MedicamentoResponse respuesta = servicioMedicamentos.actualizarSeguridad("0524", null, false, "farmacia");

        assertThat(respuesta.getAltoRiesgoMarcado()).isNull();
        assertThat(respuesta.isAltoRiesgo()).isTrue();
    }
}
