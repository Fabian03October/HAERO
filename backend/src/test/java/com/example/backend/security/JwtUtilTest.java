package com.example.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La clave de sesiones ya no cambia en cada arranque: reiniciar el backend no debe
 * invalidar las sesiones abiertas.
 */
class JwtUtilTest {

    @Test
    void sinVariableDeEntorno_creaLaClaveUnaVezYLaReutiliza(@TempDir Path carpeta) throws Exception {
        Path archivo = carpeta.resolve("hraeo").resolve("jwt-secret");

        byte[] primera = JwtUtil.obtenerSecreto("", archivo);
        byte[] segunda = JwtUtil.obtenerSecreto(null, archivo);

        assertThat(Files.exists(archivo)).isTrue();
        assertThat(primera).hasSizeGreaterThanOrEqualTo(32).isEqualTo(segunda);
    }

    @Test
    void unTokenSigueSiendoValidoDespuesDeReiniciar(@TempDir Path carpeta) {
        String archivo = carpeta.resolve("jwt-secret").toString();
        String token = new JwtUtil("", archivo).generarToken("almacen", "ALMACEN");

        // Otra instancia = el backend después de reiniciar.
        JwtUtil despuesDeReiniciar = new JwtUtil("", archivo);

        assertThat(despuesDeReiniciar.tokenValido(token)).isTrue();
        assertThat(despuesDeReiniciar.extraerNombreUsuario(token)).isEqualTo("almacen");
    }

    @Test
    void conVariableDeEntorno_usaEsaClave(@TempDir Path carpeta) {
        String secreto = "una-clave-de-prueba-suficientemente-larga-123";
        String token = new JwtUtil(secreto, carpeta.resolve("a").toString()).generarToken("farmacia", "FARMACIA");

        assertThat(new JwtUtil(secreto, carpeta.resolve("b").toString()).tokenValido(token)).isTrue();
        assertThat(Files.exists(carpeta.resolve("a"))).isFalse();
    }

    @Test
    void conVariableDeEntornoMuyCorta_noArranca(@TempDir Path carpeta) {
        assertThatThrownBy(() -> JwtUtil.obtenerSecreto("corta", carpeta.resolve("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 caracteres");
    }
}
