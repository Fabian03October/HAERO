package com.example.backend.service;

import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
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


    // ---------- Bloqueo por intentos fallidos ----------

    private LoginRequest intento(String contrasena) {
        LoginRequest request = new LoginRequest();
        request.setNombreUsuario("jperez");
        request.setContrasena(contrasena);
        return request;
    }

    @Test
    void login_conContrasenaIncorrecta_cuentaElIntentoFallido() {
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("mala", "hash-guardado")).thenReturn(false);

        assertThatThrownBy(() -> servicioAutenticacion.login(intento("mala")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Usuario o contraseña incorrectos");
        assertThat(usuarioActivo.getIntentosFallidos()).isEqualTo(1);
        assertThat(usuarioActivo.getBloqueadoHasta()).isNull();
    }

    @Test
    void login_alQuintoIntentoFallido_bloqueaLaCuenta() {
        usuarioActivo.setIntentosFallidos(ServicioAutenticacion.MAX_INTENTOS - 1);
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("mala", "hash-guardado")).thenReturn(false);

        assertThatThrownBy(() -> servicioAutenticacion.login(intento("mala")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Cuenta bloqueada");
        assertThat(usuarioActivo.getBloqueadoHasta()).isAfter(java.time.LocalDateTime.now());
        assertThat(usuarioActivo.getIntentosFallidos()).isZero();
    }

    @Test
    void login_conCuentaBloqueada_rechazaAunqueLaContrasenaSeaCorrecta() {
        usuarioActivo.setBloqueadoHasta(java.time.LocalDateTime.now().plusMinutes(5));
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));

        assertThatThrownBy(() -> servicioAutenticacion.login(intento("clave-correcta")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Intenta de nuevo en 5 minutos");
        verify(jwtUtil, never()).generarToken(any(), any());
    }

    @Test
    void login_correcto_reiniciaIntentosYQuitaBloqueoVencido() {
        usuarioActivo.setIntentosFallidos(3);
        usuarioActivo.setBloqueadoHasta(java.time.LocalDateTime.now().minusMinutes(1));
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("clave-correcta", "hash-guardado")).thenReturn(true);
        when(jwtUtil.generarToken("jperez", "FARMACIA")).thenReturn("token");

        servicioAutenticacion.login(intento("clave-correcta"));

        assertThat(usuarioActivo.getIntentosFallidos()).isZero();
        assertThat(usuarioActivo.getBloqueadoHasta()).isNull();
    }

    @Test
    void login_devuelveLaVersionDePoliticasAceptada() {
        usuarioActivo.setVersionPoliticasAceptada("1.0");
        when(usuarioRepository.findByNombreUsuario("jperez")).thenReturn(Optional.of(usuarioActivo));
        when(passwordEncoder.matches("clave-correcta", "hash-guardado")).thenReturn(true);
        when(jwtUtil.generarToken("jperez", "FARMACIA")).thenReturn("token");

        assertThat(servicioAutenticacion.login(intento("clave-correcta")).getVersionPoliticasAceptada()).isEqualTo("1.0");
    }
}
