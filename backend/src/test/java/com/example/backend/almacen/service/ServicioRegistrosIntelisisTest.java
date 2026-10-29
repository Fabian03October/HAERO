package com.example.backend.almacen.service;

import com.example.backend.almacen.service.ServicioRegistrosIntelisis.LoteHaero;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de bloqueo de la entrada (HU10) que evitan contar dos veces un lote. */
class ServicioRegistrosIntelisisTest {

    private static LoteHaero lote(String numero, String origen) {
        return new LoteHaero(1L, numero, origen, origen == null ? null : 10, origen == null ? null : numero, LocalDate.now());
    }

    @Test
    void unLoteQueEntroPorCanjeOPrestamo_noSeRecibeOtraVez() {
        assertThat(ServicioRegistrosIntelisis.yaEntroSinIntelisis("l-900", List.of(lote("L-900", null)))).isTrue();
    }

    @Test
    void unLoteRecibidoDesdeIntelisis_noCuentaComoCanje() {
        // Ese caso ya lo cubre el bloqueo "Ya se recibió en HAERO" por su registro de Intelisis.
        assertThat(ServicioRegistrosIntelisis.yaEntroSinIntelisis("L-900", List.of(lote("L-900", "COMPRA")))).isFalse();
        assertThat(ServicioRegistrosIntelisis.yaEntroSinIntelisis("L-901", List.of(lote("L-900", null)))).isFalse();
    }
}
