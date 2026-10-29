package com.example.backend.intelisis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HU38: el consumo mensual de Intelisis junta la clave base (cajas) y la "-1" (piezas). */
class ConsumoMensualIntelisisTest {

    @Test
    void juntaCajasYPiezasDeLaMismaClaveYMes() {
        List<Object[]> renglones = List.of(
                new Object[]{"010.000.0104.00", 2026, 9, new BigDecimal("3")},
                new Object[]{"010.000.0104.00-1", 2026, 9, new BigDecimal("40")},
                new Object[]{"010.000.0104.00-1", 2026, 10, new BigDecimal("-5")});

        List<Object[]> filas = new ArrayList<>(ServicioSincronizacionIntelisis.sumarConsumoMensual(renglones));

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsExactly("010.000.0104.00", "2026-09", new BigDecimal("3"), new BigDecimal("40"));
        // Más devoluciones que consumo en el mes: queda negativo y la rotación lo toma como cero.
        assertThat(filas.get(1)).containsExactly("010.000.0104.00", "2026-10", BigDecimal.ZERO, new BigDecimal("-5"));
    }
}
