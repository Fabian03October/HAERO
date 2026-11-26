package com.example.backend.farmacia.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CantidadRecetaIntelisisTest {

    @Test
    void laCantidadDeLaRecetaSeTomaComoCajas() {
        assertThat(ServicioRecetasIntelisis.piezasIndicadas(1, 10)).isEqualTo(10);   // 1 caja de paracetamol de 10
        assertThat(ServicioRecetasIntelisis.piezasIndicadas(3, 28)).isEqualTo(84);
        assertThat(ServicioRecetasIntelisis.piezasIndicadas(0.5, 10)).isEqualTo(10);
        assertThat(ServicioRecetasIntelisis.piezasIndicadas(2, null)).isEqualTo(2);
    }
}
