package com.example.backend.service;

import com.example.backend.dto.CambiarContrasenaRequest;
import com.example.backend.dto.CrearUsuarioRequest;
import com.example.backend.dto.EditarUsuarioRequest;
import com.example.backend.dto.RestablecerContrasenaRequest;
import com.example.backend.dto.UsuarioResponse;
import com.example.backend.entity.Rol;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.RolRepository;
import com.example.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioUsuariosTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ServicioUsuarios servicioUsuarios;

    private Rol rolFarmacia;
    private Usuario usuarioExistente;

    @BeforeEach
    void configurar() {
        servicioUsuarios = new ServicioUsuarios(usuarioRepository, rolRepository, passwordEncoder);

        rolFarmacia = new Rol();
        rolFarmacia.setNombre("FARMACIA");

        usuarioExistente = new Usuario();
        usuarioExistente.setId(5L);
        usuarioExistente.setNombreCompleto("Juan Perez");
        usuarioExistente.setNombreUsuario("jperez");
        usuarioExistente.setCorreo("jperez@hraeo.test");
        usuarioExistente.setContrasenaHash("hash-actual");
        usuarioExistente.setRol(rolFarmacia);
        usuarioExistente.setActivo(true);
        usuarioExistente.setDebeCambiarContrasena(false);
    }

    // ---------- crearUsuario ----------

    @Test
    void crearUsuario_conDatosValidos_loGuardaYDevuelveActivo() {
        CrearUsuarioRequest request = new CrearUsuarioRequest();
        request.setNombreCompleto("Ana Torres");
        request.setNombreUsuario("atorres");
        request.setCorreo("atorres@hraeo.test");
        request.setContrasenaTemporal("Temporal123*");
        request.setRol("ALMACEN");

        Rol rolAlmacen = new Rol();
        rolAlmacen.setNombre("ALMACEN");

        when(usuarioRepository.existsByNombreUsuario("atorres")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("atorres@hraeo.test")).thenReturn(false);
        when(rolRepository.findByNombre("ALMACEN")).thenReturn(Optional.of(rolAlmacen));
        when(passwordEncoder.encode("Temporal123*")).thenReturn("hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = servicioUsuarios.crearUsuario(request);

        assertThat(respuesta.getNombreUsuario()).isEqualTo("atorres");
        assertThat(respuesta.isActivo()).isTrue();
        assertThat(respuesta.isDebeCambiarContrasena()).isTrue();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getContrasenaHash()).isEqualTo("hash-nuevo");
    }

    @Test
    void crearUsuario_conNombreUsuarioRepetido_lanza409() {
        CrearUsuarioRequest request = new CrearUsuarioRequest();
        request.setNombreUsuario("jperez");

        when(usuarioRepository.existsByNombreUsuario("jperez")).thenReturn(true);

        assertThatThrownBy(() -> servicioUsuarios.crearUsuario(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("El nombre de usuario ya existe");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearUsuario_conContrasenaCorta_lanza400() {
        CrearUsuarioRequest request = new CrearUsuarioRequest();
        request.setNombreUsuario("nuevo");
        request.setCorreo("nuevo@hraeo.test");
        request.setContrasenaTemporal("123");

        when(usuarioRepository.existsByNombreUsuario("nuevo")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("nuevo@hraeo.test")).thenReturn(false);

        assertThatThrownBy(() -> servicioUsuarios.crearUsuario(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("al menos 8 caracteres");
    }

    @Test
    void crearUsuario_conCorreoInvalido_lanza400() {
        CrearUsuarioRequest request = new CrearUsuarioRequest();
        request.setNombreUsuario("nuevo");
        request.setCorreo("no-es-correo");
        request.setContrasenaTemporal("Temporal123*");

        when(usuarioRepository.existsByNombreUsuario("nuevo")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("no-es-correo")).thenReturn(false);

        assertThatThrownBy(() -> servicioUsuarios.crearUsuario(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("correo");
    }

    @Test
    void crearUsuario_conRolInexistente_lanza400() {
        CrearUsuarioRequest request = new CrearUsuarioRequest();
        request.setNombreUsuario("nuevo");
        request.setCorreo("nuevo@hraeo.test");
        request.setContrasenaTemporal("Temporal123*");
        request.setRol("NOEXISTE");

        when(usuarioRepository.existsByNombreUsuario("nuevo")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("nuevo@hraeo.test")).thenReturn(false);
        when(rolRepository.findByNombre("NOEXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioUsuarios.crearUsuario(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Rol inválido");
    }

    // ---------- editarUsuario ----------

    @Test
    void editarUsuario_conDatosValidos_actualizaNombreCorreoYRol() {
        EditarUsuarioRequest request = new EditarUsuarioRequest();
        request.setNombreCompleto("Juan Perez Editado");
        request.setCorreo("jperez.nuevo@hraeo.test");
        request.setRol("ADMIN");

        Rol rolAdmin = new Rol();
        rolAdmin.setNombre("ADMIN");

        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));
        when(usuarioRepository.existsByCorreoAndIdNot("jperez.nuevo@hraeo.test", 5L)).thenReturn(false);
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = servicioUsuarios.editarUsuario(5L, request);

        assertThat(respuesta.getNombreCompleto()).isEqualTo("Juan Perez Editado");
        assertThat(respuesta.getRol()).isEqualTo("ADMIN");
    }

    @Test
    void editarUsuario_conCorreoYaUsadoPorOtro_lanza409() {
        EditarUsuarioRequest request = new EditarUsuarioRequest();
        request.setCorreo("ocupado@hraeo.test");

        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));
        when(usuarioRepository.existsByCorreoAndIdNot("ocupado@hraeo.test", 5L)).thenReturn(true);

        assertThatThrownBy(() -> servicioUsuarios.editarUsuario(5L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("correo ya está registrado");
    }

    @Test
    void editarUsuario_conIdInexistente_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioUsuarios.editarUsuario(99L, new EditarUsuarioRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ---------- desactivar / reactivar ----------

    @Test
    void desactivarUsuario_aOtroUsuario_loDesactiva() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = servicioUsuarios.desactivarUsuario(5L, "admin");

        assertThat(respuesta.isActivo()).isFalse();
    }

    @Test
    void desactivarUsuario_aSiMismo_lanza400YNoLoGuarda() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));

        assertThatThrownBy(() -> servicioUsuarios.desactivarUsuario(5L, "jperez"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no puede desactivarse a sí mismo");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void reactivarUsuario_loMarcaActivo() {
        usuarioExistente.setActivo(false);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = servicioUsuarios.reactivarUsuario(5L);

        assertThat(respuesta.isActivo()).isTrue();
    }

    // ---------- restablecerContrasena / cambiarContrasenaPropia ----------

    @Test
    void restablecerContrasena_conTemporalValida_marcaDebeCambiarContrasena() {
        RestablecerContrasenaRequest request = new RestablecerContrasenaRequest();
        request.setContrasenaTemporal("NuevaTemp123*");

        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuarioExistente));
        when(passwordEncoder.encode("NuevaTemp123*")).thenReturn("hash-restablecido");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = servicioUsuarios.restablecerContrasena(5L, request);

        assertThat(respuesta.isDebeCambiarContrasena()).isTrue();
    }

    @Test
    void cambiarContrasenaPropia_conActualIncorrecta_lanza400() {
        CambiarContrasenaRequest request = new CambiarContrasenaRequest();
        request.setContrasenaActual("mala");
        request.setContrasenaNueva("NuevaValida123*");

        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioExistente));
        when(passwordEncoder.matches("mala", "hash-actual")).thenReturn(false);

        assertThatThrownBy(() -> servicioUsuarios.cambiarContrasenaPropia("jperez", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("contraseña actual no es correcta");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarContrasenaPropia_conDatosValidos_actualizaYQuitaBanderaTemporal() {
        usuarioExistente.setDebeCambiarContrasena(true);

        CambiarContrasenaRequest request = new CambiarContrasenaRequest();
        request.setContrasenaActual("hash-actual-en-claro");
        request.setContrasenaNueva("NuevaValida123*");

        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioExistente));
        when(passwordEncoder.matches("hash-actual-en-claro", "hash-actual")).thenReturn(true);
        when(passwordEncoder.encode("NuevaValida123*")).thenReturn("hash-nuevo");

        servicioUsuarios.cambiarContrasenaPropia("jperez", request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getContrasenaHash()).isEqualTo("hash-nuevo");
        assertThat(captor.getValue().isDebeCambiarContrasena()).isFalse();
    }
}
