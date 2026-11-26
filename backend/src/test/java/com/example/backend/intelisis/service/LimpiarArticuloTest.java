package com.example.backend.intelisis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimpiarArticuloTest {

    @Test
    void quitaLosPuntosDeMasDespuesDeLaClave() {
        assertThat(ServicioSincronizacionIntelisis.limpiarArticulo("010.000.5435.00.-1")).isEqualTo("010.000.5435.00-1");
        assertThat(ServicioSincronizacionIntelisis.limpiarArticulo("010.000.0269.00-1.")).isEqualTo("010.000.0269.00-1");
        assertThat(ServicioSincronizacionIntelisis.limpiarArticulo("010.000.1775.00.")).isEqualTo("010.000.1775.00");
        assertThat(ServicioSincronizacionIntelisis.limpiarArticulo("010.000.0104.00-1")).isEqualTo("010.000.0104.00-1");
        assertThat(ServicioSincronizacionIntelisis.claveBase(ServicioSincronizacionIntelisis.limpiarArticulo("010.000.5435.00.-1")))
                .isEqualTo("010.000.5435.00");
    }
}
