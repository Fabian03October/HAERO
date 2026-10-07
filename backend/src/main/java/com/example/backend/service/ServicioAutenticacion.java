package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import com.example.backend.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ServicioAutenticacion {

    // Intentos fallidos seguidos antes de bloquear la cuenta, y por cuánto tiempo.
    static final int MAX_INTENTOS = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(10);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ServicioAutenticacion(UsuarioRepository usuarioRepository,
                                   PasswordEncoder passwordEncoder,
                                   JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario())
                // mismo mensaje para usuario inexistente, inactivo o contraseña incorrecta (HU01/HU02/HU05)
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos"));

        if (!usuario.isActivo()) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            throw new BadCredentialsException(mensajeBloqueo(usuario.getBloqueadoHasta(), ahora));
        }

        if (!passwordEncoder.matches(request.getContrasena(), usuario.getContrasenaHash())) {
            // Tras MAX_INTENTOS fallos seguidos, la cuenta se bloquea un rato.
            int intentos = usuario.getIntentosFallidos() + 1;
            if (intentos >= MAX_INTENTOS) {
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
                usuarioRepository.save(usuario);
                throw new BadCredentialsException(mensajeBloqueo(usuario.getBloqueadoHasta(), ahora));
            }
            usuario.setIntentosFallidos(intentos);
            usuarioRepository.save(usuario);
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuario.setUltimoAcceso(ahora);
        usuarioRepository.save(usuario);

        String token = jwtUtil.generarToken(usuario.getNombreUsuario(), usuario.getRol().getNombre());

        return new LoginResponse(token, usuario.getRol().getNombre(), usuario.isDebeCambiarContrasena(), usuario.getNombreCompleto(),
                usuario.getVersionPoliticasAceptada(), usuario.getFechaAceptacionPoliticas());
    }

    static String mensajeBloqueo(LocalDateTime hasta, LocalDateTime ahora) {
        long minutos = Math.max(1, (long) Math.ceil(Duration.between(ahora, hasta).getSeconds() / 60.0));
        return "Cuenta bloqueada por varios intentos fallidos. Intenta de nuevo en " + minutos
                + (minutos == 1 ? " minuto" : " minutos") + " o pide al administrador que restablezca tu contraseña.";
    }

    public void logout(String nombreUsuario) {
        usuarioRepository.findByNombreUsuario(nombreUsuario).ifPresent(usuario -> {
            usuario.setTokenValidoDesde(LocalDateTime.now());
            usuarioRepository.save(usuario);
        });
    }
}