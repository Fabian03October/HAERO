package com.example.backend.intelisis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimpiarArticuloTest {

    private static String limpio(String articulo) {
        return ServicioSincronizacionIntelisis.limpiarArticulo(articulo);
    }

    @Test
    void cualquierVarianteDelCodigoEsLaMismaClave() {
        assertThat(limpio("G-010.000.0570.00")).isEqualTo("010.000.0570.00");
        assertThat(limpio("G-010.000.0570.00-1")).isEqualTo("010.000.0570.00-1");
        assertThat(limpio("010.000.5435.00.-1")).isEqualTo("010.000.5435.00-1");
        assertThat(limpio("010.000.0269.00-1.")).isEqualTo("010.000.0269.00-1");
        assertThat(limpio("010.000.1775.00.")).isEqualTo("010.000.1775.00");
        assertThat(limpio("010.000.2130.00a-1")).isEqualTo("010.000.2130.00-1");
        assertThat(limpio("010.000.4186.03-01")).isEqualTo("010.000.4186.03-1");
        assertThat(limpio("010.000.5646.0-1")).isEqualTo("010.000.5646.00-1");
        assertThat(limpio("010.000.0104.00-1")).isEqualTo("010.000.0104.00-1");
        assertThat(limpio("040.000.2108.00")).isEqualTo("040.000.2108.00");
        assertThat(ServicioSincronizacionIntelisis.claveBase(limpio("G-010.000.0570.00-1"))).isEqualTo("010.000.0570.00");
    }

    @Test
    void loQueNoEsMedicamentoSeDejaIgual() {
        assertThat(limpio("25401-00718")).isEqualTo("25401-00718");
        assertThat(limpio("060.371.2514")).isEqualTo("060.371.2514");
    }
}
