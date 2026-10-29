package com.example.backend.service;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.LoginResponse;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import com.example.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServicioAutenticacion {

    // Intentos fallidos seguidos antes de bloquear la cuenta, y por cuánto tiempo.
    static final int MAX_INTENTOS = 5;
    static final Duration DURACION_BLOQUEO = Duration.ofMinutes(10);

    // Límite de nombres desconocidos que se recuerdan; si se llena, se vacía.
    private static final int MAX_DESCONOCIDOS = 10_000;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    // true: entrar con una cuenta cierra sus otras sesiones abiertas.
    private final boolean sesionUnica;
    // Intentos fallidos con nombres que no existen o están desactivados: se bloquean igual
    // que una cuenta real, para que el mensaje de bloqueo no revele qué usuarios existen (HU01 CA2).
    private final Map<String, Intentos> intentosDesconocidos = new ConcurrentHashMap<>();

    private static final class Intentos {
        int fallidos;
        LocalDateTime bloqueadoHasta;
    }

    @Autowired
    public ServicioAutenticacion(UsuarioRepository usuarioRepository,
                                   PasswordEncoder passwordEncoder,
                                   JwtUtil jwtUtil,
                                   @Value("${seguridad.sesion-unica:true}") boolean sesionUnica) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.sesionUnica = sesionUnica;
    }

    ServicioAutenticacion(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this(usuarioRepository, passwordEncoder, jwtUtil, true);
    }

    public LoginResponse login(LoginRequest request) {
        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = usuarioRepository.findByNombreUsuario(request.getNombreUsuario()).orElse(null);

        // Mismo trato para usuario inexistente o inactivo que para contraseña incorrecta (HU01/HU02/HU05).
        if (usuario == null || !usuario.isActivo()) {
            throw fallidoDesconocido(request.getNombreUsuario(), ahora);
        }

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
        // La sesión queda válida desde este inicio de sesión. Se guarda con la hora de
        // la aplicación (misma zona y reloj que el iat del token) y truncada al segundo,
        // porque el iat del JWT solo tiene precisión de segundos; así el token recién
        // emitido nunca nace "anterior" a esta marca por milisegundos ni por diferencias
        // de zona horaria con la base de datos (p. ej. SQL Server en UTC dentro de Docker).
        // Con sesión única, las sesiones anteriores de esta cuenta dejan de valer.
        if (sesionUnica || usuario.getTokenValidoDesde() == null) {
            usuario.setTokenValidoDesde(ahora.truncatedTo(ChronoUnit.SECONDS));
        }
        usuarioRepository.save(usuario);

        String token = jwtUtil.generarToken(usuario.getNombreUsuario(), usuario.getRol().getNombre());

        return new LoginResponse(token, usuario.getRol().getNombre(), usuario.isDebeCambiarContrasena(), usuario.getNombreCompleto(),
                usuario.getVersionPoliticasAceptada(), usuario.getFechaAceptacionPoliticas());
    }

    private BadCredentialsException fallidoDesconocido(String nombreUsuario, LocalDateTime ahora) {
        String llave = nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
        if (intentosDesconocidos.size() > MAX_DESCONOCIDOS) {
            intentosDesconocidos.clear();
        }
        Intentos intentos = intentosDesconocidos.computeIfAbsent(llave, k -> new Intentos());
        synchronized (intentos) {
            if (intentos.bloqueadoHasta != null && intentos.bloqueadoHasta.isAfter(ahora)) {
                return new BadCredentialsException(mensajeBloqueo(intentos.bloqueadoHasta, ahora));
            }
            intentos.fallidos++;
            if (intentos.fallidos >= MAX_INTENTOS) {
                intentos.fallidos = 0;
                intentos.bloqueadoHasta = ahora.plus(DURACION_BLOQUEO);
                return new BadCredentialsException(mensajeBloqueo(intentos.bloqueadoHasta, ahora));
            }
        }
        return new BadCredentialsException("Usuario o contraseña incorrectos");
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