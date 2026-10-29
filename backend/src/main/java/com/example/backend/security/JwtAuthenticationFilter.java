package com.example.backend.security;

import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Duration LIMITE_INACTIVIDAD = Duration.ofMinutes(15);

    // Lo único que se puede hacer mientras falta cambiar la contraseña temporal o aceptar
    // las políticas (HU08 CA4): antes solo lo exigía el navegador.
    private static final List<String> RUTAS_SIN_PENDIENTES = List.of(
            "/api/usuarios/me/contrasena", "/api/usuarios/me/politicas", "/api/auth/logout");

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;
    private final String versionPoliticas;
    private final ConcurrentHashMap<String, LocalDateTime> ultimaActividadPorUsuario = new ConcurrentHashMap<>();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UsuarioRepository usuarioRepository,
                                   @Value("${politicas.version:1.0}") String versionPoliticas) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
        this.versionPoliticas = versionPoliticas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.tokenValido(token)) {
                Usuario usuario = autenticarSiCorresponde(token, response);
                String pendiente = usuario == null ? null : pendiente(usuario);
                if (pendiente != null && !RUTAS_SIN_PENDIENTES.contains(request.getRequestURI())) {
                    rechazarPorPendiente(response, pendiente);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Qué le falta al usuario antes de poder usar el sistema, o null si nada. */
    private String pendiente(Usuario usuario) {
        if (usuario.isDebeCambiarContrasena()) {
            return "cambiar-contrasena";
        }
        if (!versionPoliticas.equals(usuario.getVersionPoliticasAceptada())) {
            return "aceptar-politicas";
        }
        return null;
    }

    private void rechazarPorPendiente(HttpServletResponse response, String pendiente) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader("X-Auth-Pendiente", pendiente);
        response.setContentType("application/json;charset=UTF-8");
        String mensaje = "cambiar-contrasena".equals(pendiente)
                ? "Cambia tu contraseña temporal antes de usar el sistema"
                : "Acepta las políticas de uso y privacidad antes de usar el sistema";
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }

    /** Autentica la petición y regresa el usuario, o null si la sesión no es válida. */
    private Usuario autenticarSiCorresponde(String token, HttpServletResponse response) {
        String nombreUsuario = jwtUtil.extraerNombreUsuario(token);
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombreUsuario(nombreUsuario);

        if (usuarioOpt.isEmpty() || !usuarioOpt.get().isActivo()) {
            response.setHeader("X-Auth-Error", "cuenta-inactiva");
            return null;
        }

        Usuario usuario = usuarioOpt.get();
        LocalDateTime fechaEmision = jwtUtil.extraerFechaEmision(token);

        // El iat del JWT solo guarda segundos; sin truncar, un login hecho en el mismo
        // segundo que el cierre de sesión o el cambio de contraseña nacería inválido.
        if (fechaEmision.isBefore(usuario.getTokenValidoDesde().truncatedTo(ChronoUnit.SECONDS))) {
            response.setHeader("X-Auth-Error", "sesion-cerrada");
            return null;
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime ultimaActividad = ultimaActividadPorUsuario.get(nombreUsuario);

        // La actividad se guarda por usuario: si el token se emitió después de la última
        // actividad registrada es un login nuevo y la inactividad anterior no cuenta.
        if (ultimaActividad != null && fechaEmision.isBefore(ultimaActividad)
                && Duration.between(ultimaActividad, ahora).compareTo(LIMITE_INACTIVIDAD) > 0) {
            ultimaActividadPorUsuario.remove(nombreUsuario);
            response.setHeader("X-Auth-Error", "inactividad");
            return null;
        }

        ultimaActividadPorUsuario.put(nombreUsuario, ahora);

        // El rol sale de la base de datos y no del token: si el administrador lo cambia,
        // los permisos nuevos aplican desde la siguiente petición (HU07 CA3).
        String rol = usuario.getRol().getNombre();
        var authentication = new UsernamePasswordAuthenticationToken(
                nombreUsuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        return usuario;
    }
}
