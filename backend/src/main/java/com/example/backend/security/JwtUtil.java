package com.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class JwtUtil {

    // En un proyecto real esta clave va en application.properties, no aquí
    private final SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    // Techo de seguridad absoluto del token; el cierre real por 15 min de inactividad
    // (HU09) lo aplica JwtAuthenticationFilter comparando la última actividad registrada.
    private final long EXPIRACION_MS = 8 * 60 * 60 * 1000;

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