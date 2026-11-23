package com.example.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // Direcciones desde las que se abre el frontend. En el servidor se ponen con la
    // variable CORS_ORIGENES (separadas por coma); si el frontend se sirve desde el mismo
    // servidor que reenvía /api, no hace falta agregar nada.
    private final List<String> origenesPermitidos;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${CORS_ORIGENES:http://localhost:4200,http://localhost:4300}") List<String> origenesPermitidos) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.origenesPermitidos = origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origenesPermitidos);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Auth-Error", "X-Auth-Pendiente"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401 = sin sesión o sesión vencida (el front manda al login); 403 = la sesión es válida
                // pero el rol no tiene permiso. Antes las dos salían como 403 vacío.
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, e) -> responder(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Tu sesión no es válida o expiró. Vuelve a iniciar sesión."))
                        .accessDeniedHandler((request, response, e) -> responder(response, HttpServletResponse.SC_FORBIDDEN,
                                "No tiene permisos para realizar esta acción")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/usuarios/me/**").authenticated()
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        // Farmacia solo consulta el catálogo para capturar sus solicitudes (HU13).
                        .requestMatchers(HttpMethod.GET, "/api/almacen/medicamentos/**").hasAnyRole("ALMACEN", "SUPERVISION", "FARMACIA")
                        .requestMatchers(HttpMethod.GET, "/api/almacen/**").hasAnyRole("ALMACEN", "SUPERVISION")
                        .requestMatchers("/api/almacen/**").hasRole("ALMACEN")
                        // Almacén ve lo que Farmacia recibió de sus despachos y las diferencias (HU26 CA2).
                        .requestMatchers(HttpMethod.GET, "/api/farmacia/recepciones").hasAnyRole("FARMACIA", "SUPERVISION", "ALMACEN")
                        // Hospitalización: datos clínicos de internados; solo la usa Farmacia.
                        .requestMatchers("/api/hospitalizacion/**").hasRole("FARMACIA")
                        // Solicitudes de Oncología (diagnóstico y nota del médico): solo Farmacia.
                        .requestMatchers("/api/farmacia/quimioterapia/intelisis/**").hasRole("FARMACIA")
                        .requestMatchers(HttpMethod.GET, "/api/farmacia/**").hasAnyRole("FARMACIA", "SUPERVISION")
                        .requestMatchers("/api/farmacia/**").hasRole("FARMACIA")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Mismo formato que GlobalExceptionHandler: {"mensaje": "..."}. Los mensajes son fijos, sin comillas.
    private static void responder(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\"}");
    }
}
