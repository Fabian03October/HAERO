package com.example.backend.intelisis.service;

import com.example.backend.intelisis.dto.LoteConciliadoResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConciliacionLotesTest {

    private static final LocalDate CAD = LocalDate.of(2027, 5, 31);

    @Test
    void sumaAlmacenYFarmaciaEnPiezasYJuntaElLoteConSufijoDeIntelisis() {
        List<LoteConciliadoResponse> lotes = ServicioIntelisis.juntarPorLote(List.of(
                new ServicioIntelisis.FilaLote("2503353", CAD, 3, 0, 0, null),          // Almacén: 3 cajas
                new ServicioIntelisis.FilaLote("2503353", CAD, 0, 5, 0, null),          // Farmacia: 5 piezas
                new ServicioIntelisis.FilaLote("2503353 SCC", CAD, 0, 0, 60, "2503353 SCC"),
                new ServicioIntelisis.FilaLote("2503353", CAD, 0, 0, 5, "2503353")), 20);
        assertThat(lotes).hasSize(1);
        LoteConciliadoResponse l = lotes.get(0);
        assertThat(l.lote()).isEqualTo("2503353");
        assertThat(l.haeroPiezas()).isEqualTo(65);
        assertThat(l.intelisisPiezas()).isEqualTo(65);
        assertThat(l.lotesIntelisis()).containsExactly("2503353", "2503353 SCC");
    }

    @Test
    void loteSoloEnUnLadoYCajasSinFactor() {
        List<LoteConciliadoResponse> lotes = ServicioIntelisis.juntarPorLote(List.of(
                new ServicioIntelisis.FilaLote("A1", CAD, 2, 0, 0, null),
                new ServicioIntelisis.FilaLote("B2", CAD.plusDays(1), 0, 0, 7, "B2")), null);
        assertThat(lotes).extracting(LoteConciliadoResponse::lote).containsExactly("A1", "B2");
        assertThat(lotes.get(0).haeroPiezas()).isNull();          // 2 cajas sin saber cuántas piezas traen
        assertThat(lotes.get(0).intelisisPiezas()).isNull();      // no está en Intelisis
        assertThat(lotes.get(1).haeroPiezas()).isZero();
        assertThat(lotes.get(1).intelisisPiezas()).isEqualTo(7);
    }
}
