package com.example.backend.security;

import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Duration LIMITE_INACTIVIDAD = Duration.ofMinutes(15);

    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;
    private final ConcurrentHashMap<String, LocalDateTime> ultimaActividadPorUsuario = new ConcurrentHashMap<>();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UsuarioRepository usuarioRepository) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.tokenValido(token)) {
                autenticarSiCorresponde(token, response);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void autenticarSiCorresponde(String token, HttpServletResponse response) {
        String nombreUsuario = jwtUtil.extraerNombreUsuario(token);
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombreUsuario(nombreUsuario);

        if (usuarioOpt.isEmpty() || !usuarioOpt.get().isActivo()) {
            response.setHeader("X-Auth-Error", "cuenta-inactiva");
            return;
        }

        Usuario usuario = usuarioOpt.get();
        LocalDateTime fechaEmision = jwtUtil.extraerFechaEmision(token);

        if (fechaEmision.isBefore(usuario.getTokenValidoDesde())) {
            response.setHeader("X-Auth-Error", "sesion-cerrada");
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime ultimaActividad = ultimaActividadPorUsuario.get(nombreUsuario);

        if (ultimaActividad != null && Duration.between(ultimaActividad, ahora).compareTo(LIMITE_INACTIVIDAD) > 0) {
            ultimaActividadPorUsuario.remove(nombreUsuario);
            response.setHeader("X-Auth-Error", "inactividad");
            return;
        }

        ultimaActividadPorUsuario.put(nombreUsuario, ahora);

        String rol = jwtUtil.extraerRol(token);
        var authentication = new UsernamePasswordAuthenticationToken(
                nombreUsuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
