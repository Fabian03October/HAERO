package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Medicamento;
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

    private static Medicamento conConcentracion(String concentracion) {
        Medicamento m = new Medicamento();
        m.setConcentracion(concentracion);
        return m;
    }

    @Test
    void lasPiezasDelDiaSalenDeLaDosisYDeLoQueTraeCadaPieza() {
        // 2 g cada 12 h con frascos de 1 g: 2 por dosis x 2 dosis.
        assertThat(ServicioCarrito.piezasDelDia(2f, "GRAMO", 2, conConcentracion("1 g"))).isEqualTo(4);
        // En cajas se dan cajas: 1 caja al día de 30 = 30 piezas.
        Medicamento caja = conConcentracion("10 mg");
        caja.setPiezasPorCaja(30);
        assertThat(ServicioCarrito.piezasDelDia(1f, "CAJA", 1, caja)).isEqualTo(30);
        // 40 mg cada 24 h con cápsulas de 20 mg.
        assertThat(ServicioCarrito.piezasDelDia(40f, "MILIGRAMO", 1, conConcentracion("20 mg"))).isEqualTo(2);
        // 1 g cada 8 h con frascos de 1 g: una pieza por dosis.
        assertThat(ServicioCarrito.piezasDelDia(1f, "GRAMO", 3, conConcentracion("1 g"))).isEqualTo(3);
        // Indicado ya en piezas: 2 tabletas cada 8 h.
        assertThat(ServicioCarrito.piezasDelDia(2f, "TABLETA", 3, null)).isEqualTo(6);
        // "75 TABLETA" de pregabalina 75 mg: son 75 mg, una cápsula.
        assertThat(ServicioCarrito.piezasDelDia(75f, "TABLETA", 1, conConcentracion("75 mg"))).isEqualTo(1);
    }

    @Test
    void sinDatosParaCompararNoSeCalculan() {
        assertThat(ServicioCarrito.piezasDelDia(10f, "mililitros", 3, conConcentracion("1 g"))).isNull();
        assertThat(ServicioCarrito.piezasDelDia(null, "MILIGRAMO", 3, conConcentracion("20 mg"))).isNull();
        assertThat(ServicioCarrito.piezasDelDia(40f, "MILIGRAMO", 1, null)).isNull();
        // Suero de 500 ml: "0.9 g/100 ml" no es lo que trae la bolsa.
        assertThat(ServicioCarrito.piezasDelDia(500f, "mililitros", 1, conConcentracion("0.9 g/100 ml"))).isNull();
        // Suspensión oral 250 mg/5 ml: la medida es para tomar, no el frasco.
        Medicamento suspension = conConcentracion("250 mg/5 ml");
        suspension.setFormaFarmaceutica("SUSPENSION ORAL");
        assertThat(ServicioCarrito.piezasDelDia(250f, "MILIGRAMO", 3, suspension)).isNull();
        // Ampolleta de 300 mg/2 ml para 600 mg cada 8 h: 2 por dosis.
        assertThat(ServicioCarrito.piezasDelDia(600f, "MILIGRAMO", 3, conConcentracion("300 MG/2 ML"))).isEqualTo(6);
    }
}
