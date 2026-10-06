package com.example.backend.security;

import com.example.backend.entity.Rol;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Filtro que autentica cada petición a partir del JWT. Cubre las tres formas
 * de rechazar una sesión aunque el token siga "firmado y vigente": cuenta
 * desactivada, contraseña cambiada/cerrada sesión (tokenValidoDesde) y
 * 15 minutos de inactividad.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filtro;

    @BeforeEach
    void configurar() {
        filtro = new JwtAuthenticationFilter(jwtUtil, usuarioRepository);
    }

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuarioActivo(String rol) {
        Rol r = new Rol();
        r.setNombre(rol);
        Usuario u = new Usuario();
        u.setNombreUsuario("almacen");
        u.setActivo(true);
        u.setRol(r);
        u.setTokenValidoDesde(LocalDateTime.now().minusDays(1));
        return u;
    }

    @SuppressWarnings("unchecked")
    private void simularUltimaActividad(String nombreUsuario, LocalDateTime momento) throws Exception {
        Field campo = JwtAuthenticationFilter.class.getDeclaredField("ultimaActividadPorUsuario");
        campo.setAccessible(true);
        ((Map<String, LocalDateTime>) campo.get(filtro)).put(nombreUsuario, momento);
    }

    @Test
    void sinHeaderAuthorization_noAutenticaYContinuaLaCadena() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filtro.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void conTokenInvalido_noAutentica() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-malo");
        when(jwtUtil.tokenValido("token-malo")).thenReturn(false);

        filtro.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void conUsuarioQueYaNoExiste_marcaCuentaInactivaYNoAutentica() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-bueno");
        when(jwtUtil.tokenValido("token-bueno")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-bueno")).thenReturn("fantasma");
        when(usuarioRepository.findByNombreUsuario("fantasma")).thenReturn(Optional.empty());

        filtro.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Auth-Error", "cuenta-inactiva");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void conUsuarioDesactivado_marcaCuentaInactivaYNoAutentica() throws Exception {
        Usuario desactivado = usuarioActivo("ALMACEN");
        desactivado.setActivo(false);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-bueno");
        when(jwtUtil.tokenValido("token-bueno")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-bueno")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(desactivado));

        filtro.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Auth-Error", "cuenta-inactiva");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void conTokenEmitidoAntesDeTokenValidoDesde_marcaSesionCerrada() throws Exception {
        // tokenValidoDesde avanza cuando el usuario cierra sesión, cambia su
        // contraseña o el administrador se la restablece: cualquier token
        // emitido antes de ese instante debe quedar invalidado de inmediato.
        Usuario usuario = usuarioActivo("ALMACEN");
        usuario.setTokenValidoDesde(LocalDateTime.now());

        when(request.getHeader("Authorization")).thenReturn("Bearer token-viejo");
        when(jwtUtil.tokenValido("token-viejo")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-viejo")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-viejo")).thenReturn(LocalDateTime.now().minusMinutes(5));

        filtro.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Auth-Error", "sesion-cerrada");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void conTokenValidoYPrimeraActividad_autenticaConElRolCorrecto() throws Exception {
        Usuario usuario = usuarioActivo("ALMACEN");

        when(request.getHeader("Authorization")).thenReturn("Bearer token-bueno");
        when(jwtUtil.tokenValido("token-bueno")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-bueno")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-bueno")).thenReturn(LocalDateTime.now());
        when(jwtUtil.extraerRol("token-bueno")).thenReturn("ALMACEN");

        filtro.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("almacen");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ALMACEN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void conMenosDeQuinceMinutosDeInactividad_siguePermitiendoElAcceso() throws Exception {
        Usuario usuario = usuarioActivo("FARMACIA");

        when(request.getHeader("Authorization")).thenReturn("Bearer token-bueno");
        when(jwtUtil.tokenValido("token-bueno")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-bueno")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-bueno")).thenReturn(LocalDateTime.now());
        when(jwtUtil.extraerRol("token-bueno")).thenReturn("FARMACIA");

        simularUltimaActividad("almacen", LocalDateTime.now().minusMinutes(10));

        filtro.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(response, org.mockito.Mockito.never()).setHeader("X-Auth-Error", "inactividad");
    }

    @Test
    void conMasDeQuinceMinutosDeInactividad_marcaInactividadYNoAutentica() throws Exception {
        Usuario usuario = usuarioActivo("ALMACEN");

        when(request.getHeader("Authorization")).thenReturn("Bearer token-bueno");
        when(jwtUtil.tokenValido("token-bueno")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-bueno")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-bueno")).thenReturn(LocalDateTime.now().minusMinutes(30));

        simularUltimaActividad("almacen", LocalDateTime.now().minusMinutes(20));

        filtro.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Auth-Error", "inactividad");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void conLoginNuevoDespuesDeInactividadVieja_noMarcaInactividad() throws Exception {
        // Otro usuario entra horas después de su última actividad: el token es
        // nuevo, así que la actividad vieja no debe cerrarle la sesión.
        Usuario usuario = usuarioActivo("ALMACEN");

        when(request.getHeader("Authorization")).thenReturn("Bearer token-nuevo");
        when(jwtUtil.tokenValido("token-nuevo")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-nuevo")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-nuevo")).thenReturn(LocalDateTime.now());
        when(jwtUtil.extraerRol("token-nuevo")).thenReturn("ALMACEN");

        simularUltimaActividad("almacen", LocalDateTime.now().minusHours(3));

        filtro.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(response, org.mockito.Mockito.never()).setHeader("X-Auth-Error", "inactividad");
    }

    @Test
    void conTokenEmitidoEnElMismoSegundoQueTokenValidoDesde_autentica() throws Exception {
        // El iat del JWT no guarda milisegundos: un login inmediato tras cambiar
        // la contraseña no debe tomarse como sesión cerrada.
        LocalDateTime invalidado = LocalDateTime.now().withNano(700_000_000);
        Usuario usuario = usuarioActivo("ALMACEN");
        usuario.setTokenValidoDesde(invalidado);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-nuevo");
        when(jwtUtil.tokenValido("token-nuevo")).thenReturn(true);
        when(jwtUtil.extraerNombreUsuario("token-nuevo")).thenReturn("almacen");
        when(usuarioRepository.findByNombreUsuario("almacen")).thenReturn(Optional.of(usuario));
        when(jwtUtil.extraerFechaEmision("token-nuevo")).thenReturn(invalidado.withNano(0));
        when(jwtUtil.extraerRol("token-nuevo")).thenReturn("ALMACEN");

        filtro.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(response, org.mockito.Mockito.never()).setHeader("X-Auth-Error", "sesion-cerrada");
    }
}
