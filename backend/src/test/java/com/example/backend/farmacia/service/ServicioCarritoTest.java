package com.example.backend.farmacia.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carrito de Hospitalización: dosis del día sugeridas según la frecuencia y folio de la receta del día.
 */
class ServicioCarritoTest {

    @Test
    void lasDosisDelDiaSalenDeLaFrecuencia() {
        assertThat(ServicioCarrito.dosisDelDia(8, "horas")).isEqualTo(3);
        assertThat(ServicioCarrito.dosisDelDia(6, "HORAS")).isEqualTo(4);
        assertThat(ServicioCarrito.dosisDelDia(24, "horas")).isEqualTo(1);
        assertThat(ServicioCarrito.dosisDelDia(5, "horas")).isEqualTo(5);
        assertThat(ServicioCarrito.dosisDelDia(1, "dias")).isEqualTo(1);
        assertThat(ServicioCarrito.dosisDelDia(null, null)).isEqualTo(1);
        assertThat(ServicioCarrito.dosisDelDia(0, "horas")).isEqualTo(1);
    }

    @Test
    void laRecetaDelCarritoEsUnaPorPacienteYDia() {
        assertThat(ServicioCarrito.folio(LocalDate.of(2026, 10, 27), 123456)).isEqualTo("HOSP-20261027-123456");
    }
}
