package com.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    // Clave fija con la que se firman las sesiones. Antes se generaba al azar en
    // cada arranque y reiniciar el backend cerraba la sesión de todos.
    private final SecretKey key;
    // Techo de seguridad absoluto del token; el cierre real por 15 min de inactividad
    // (HU09) lo aplica JwtAuthenticationFilter comparando la última actividad registrada.
    private final long EXPIRACION_MS = 8 * 60 * 60 * 1000;

    /**
     * La clave sale, en este orden, de:
     * 1. la variable de entorno JWT_SECRET (texto de al menos 32 caracteres), o
     * 2. el archivo JWT_SECRET_FILE (por defecto ~/.hraeo/jwt-secret). Si no existe,
     *    se crea una clave aleatoria y se guarda ahí para reutilizarla en cada arranque.
     * El archivo queda fuera del repositorio, así cada equipo tiene la suya.
     */
    public JwtUtil(@Value("${JWT_SECRET:}") String secreto,
                   @Value("${JWT_SECRET_FILE:${user.home}/.hraeo/jwt-secret}") String archivo) {
        this.key = Keys.hmacShaKeyFor(obtenerSecreto(secreto, Path.of(archivo)));
    }

    static byte[] obtenerSecreto(String secreto, Path archivo) {
        if (secreto != null && !secreto.isBlank()) {
            byte[] bytes = secreto.trim().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
            }
            return bytes;
        }
        try {
            if (Files.exists(archivo)) {
                byte[] guardado = Base64.getDecoder().decode(Files.readString(archivo, StandardCharsets.UTF_8).trim());
                if (guardado.length >= 32) {
                    return guardado;
                }
            }
            byte[] nuevo = new byte[64];
            new SecureRandom().nextBytes(nuevo);
            Files.createDirectories(archivo.toAbsolutePath().getParent());
            Files.writeString(archivo, Base64.getEncoder().encodeToString(nuevo), StandardCharsets.UTF_8);
            log.info("Se creó la clave de sesiones en {}", archivo.toAbsolutePath());
            return nuevo;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer ni crear la clave de sesiones en " + archivo.toAbsolutePath(), e);
        }
    }

    public String generarToken(String nombreUsuario, String rol) {
        return Jwts.builder()
                .subject(nombreUsuario)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRACION_MS))
                .signWith(key)
                .compact();
    }

    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extraerNombreUsuario(String token) {
        return extraerClaims(token).getSubject();
    }

    public String extraerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    public LocalDateTime extraerFechaEmision(String token) {
        return extraerClaims(token).getIssuedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public boolean tokenValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}