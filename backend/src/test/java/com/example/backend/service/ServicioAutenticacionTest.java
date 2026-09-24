package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Rol;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import com.example.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicioAutenticacionTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private ServicioAutenticacion servicioAutenticacion;

    private Usuario usuarioActivo;

    @BeforeEach
    void configurar() {
        servicioAutenticacion = new ServicioAutenticacion(usuarioRepository, passwordEncoder, jwtUtil);

        Rol rol = new Rol();
        rol.setNombre("FARMACIA");

        usuarioActivo = new Usuario();
        usuarioActivo.setNombreUsuario("jperez");
        usuarioActivo.setNombreCompleto("Juan Perez");
        usuarioActivo.setContrasenaHash("hash-guardado");
        usuarioActivo.setActivo(true);
        usuarioActivo.setDebeCambiarContrasena(false);
        usuarioActivo.setRol(rol);
    }

    @Test
    void login_conCredencialesCorrectas_devuelveTokenYDatosDelUsuario() {
        LoginRequest request = new LoginRequest();
        request.setNombreUsuario("jperez");
        request.setContrasena("clave-correcta");

        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("clave-correcta", "hash-guardado")).thenReturn(true);
        when(jwtUtil.generarToken("jperez", "FARMACIA")).thenReturn("token-de-prueba");

        LoginResponse respuesta = servicioAutenticacion.login(request);

        assertThat(respuesta.getToken()).isEqualTo("token-de-prueba");
        assertThat(respuesta.getRol()).isEqualTo("FARMACIA");
        assertThat(respuesta.getNombreCompleto()).isEqualTo("Juan Perez");
        verify(usuarioRepository).save(usuarioActivo);
    }

    @Test
    void login_conUsuarioInexistente_lanzaCredencialesInvalidas() {
        LoginRequest request = new LoginRequest();
        request.setNombreUsuario("no-existe");
        request.setContrasena("cualquiera");

        when(usuarioRepository.findByNombreUsuario("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioAutenticacion.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Usuario o contraseña incorrectos");
    }

    @Test
    void login_conUsuarioInactivo_lanzaCredencialesInvalidas() {
        usuarioActivo.setActivo(false);

        LoginRequest request = new LoginRequest();
        request.setNombreUsuario("jperez");
        request.setContrasena("clave-correcta");

        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));

        assertThatThrownBy(() -> servicioAutenticacion.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Usuario o contraseña incorrectos");
    }

    @Test
    void login_conContrasenaIncorrecta_lanzaCredencialesInvalidas() {
        LoginRequest request = new LoginRequest();
        request.setNombreUsuario("jperez");
        request.setContrasena("clave-mala");

        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("clave-mala", "hash-guardado")).thenReturn(false);

        assertThatThrownBy(() -> servicioAutenticacion.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Usuario o contraseña incorrectos");
    }
}
