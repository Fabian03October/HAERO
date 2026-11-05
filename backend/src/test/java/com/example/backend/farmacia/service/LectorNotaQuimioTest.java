package com.example.backend.farmacia.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LectorNotaQuimioTest {

    // Catálogo mínimo con los nombres tal como vienen de Intelisis.
    private static final List<LectorNotaQuimio.Farmaco> CATALOGO = List.of(
            farmaco("010.000.5435.00", "PACLITAXEL"),
            farmaco("010.000.4241.00", "DEXAMETASONA, FOSFATO SODICO DE (GT10)"),
            farmaco("010.000.3432.00", "Dexametasona. Tableta. Cada tableta contiene dexametasona 0.5 mg"),
            farmaco("010.000.0405.00", "Difenhidramina. Solución inyectable"),
            farmaco("010.000.1775.00", "CITARABINA"),
            farmaco("010.000.1776.00", "METOTREXATO. Solución inyectable"),
            farmaco("010.000.6016.00", "APREPITANT. Cápsula"),
            farmaco("010.000.3608.00", "CLORURO DE SODIO. Solución inyectable al 0.9%"),
            farmaco("010.000.1706.00", "ÁCIDO FÓLICO. Tableta"),
            farmaco("010.000.5445.00", "RITUXIMAB"),
            farmaco("010.000.3610.00", "SODIO, BICARBONATO DE. Solución inyectable"),
            farmaco("010.000.9999.00", "Tableta Cada tableta contiene"),
            farmaco("010.000.6075.00", "PEMBROLIZUMAB"),
            farmaco("010.000.0104.00", "PARACETAMOL. Solución inyectable"),
            farmaco("010.000.1770.00", "VINCRISTINA"));

    private static LectorNotaQuimio.Farmaco farmaco(String clave, String nombre) {
        return new LectorNotaQuimio.Farmaco(clave, LectorNotaQuimio.palabrasFarmaco(nombre));
    }

    @Test
    void palabrasDelFarmacoSinPresentacionNiAcentos() {
        assertThat(LectorNotaQuimio.palabrasFarmaco("PACLITAXEL, SOLUCIÓN INYECTABLE DE 300 MG")).containsExactly("PACLITAXEL");
        assertThat(LectorNotaQuimio.palabrasFarmaco("Dexametasona. Tableta")).containsExactly("DEXAMETASONA");
        assertThat(LectorNotaQuimio.palabrasFarmaco("CLORURO DE SODIO. Solución")).containsExactly("CLORURO", "SODIO");
        assertThat(LectorNotaQuimio.palabrasFarmaco("ÁCIDO FÓLICO")).containsExactly("ACIDO", "FOLICO");
    }

    @Test
    void elVehiculoNoEsOtroMedicamento() {
        var hallazgos = LectorNotaQuimio.leer("PACLITAXEL  230  MG  EN  500  CC DE  SOL  FIS  P/3 HRS", CATALOGO);
        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.get(0).farmaco()).isEqualTo("PACLITAXEL");
        assertThat(hallazgos.get(0).dosisCantidad()).isEqualByComparingTo(new BigDecimal("230"));
        assertThat(hallazgos.get(0).dosisUnidad()).isEqualTo("mg");

        var dilucion = LectorNotaQuimio.leer("DEXAMETASONA 8 MG EN 100 ML DE SOL. CLORURO DE SODIO AL 0.9% P/ 10 MIN", CATALOGO);
        assertThat(dilucion).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("DEXAMETASONA");
        // Las dos claves de dexametasona del catálogo se ofrecen; Farmacia elige.
        assertThat(dilucion.get(0).claves()).containsExactlyInAnyOrder("010.000.4241.00", "010.000.3432.00");
    }

    @Test
    void variosMedicamentosEnUnRenglonEnElOrdenEscrito() {
        var hallazgos = LectorNotaQuimio.leer("QUIMIOTERAPIA INTRATECAL\r\n\r\nMETOTREXATE 12.5MG   +  CITARABINA 50MG   + DEXAMETASONA 5 MG", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("METOTREXATO", "CITARABINA", "DEXAMETASONA");
        assertThat(hallazgos.get(0).dosisCantidad()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(hallazgos.get(0).texto()).contains("METOTREXATE");
    }

    @Test
    void nombresConLaUltimaLetraDistinta() {
        var hallazgos = LectorNotaQuimio.leer("APREPITAN 125 MG VO DIA 1, 80 MG DIA 2 Y 3", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("APREPITANT");
        assertThat(hallazgos.get(0).dosisCantidad()).isEqualByComparingTo(new BigDecimal("125"));
        assertThat(LectorNotaQuimio.parecida("CISPLATINO", "CARBOPLATINO")).isFalse();
    }

    @Test
    void laSolucionDeHidratacionSiCuentaYLaDosisPorMetroCuadradoNo() {
        var hallazgos = LectorNotaQuimio.leer("SOLUCION CLORURO DE SODIO 0.9% 250 ML P/ 20 MIN\nRituximab 375 mg/m2 IV", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("CLORURO SODIO", "RITUXIMAB");
        // 375 mg/m2 depende de la superficie corporal: no es la dosis a preparar.
        assertThat(hallazgos.get(1).dosisCantidad()).isNull();
    }

    @Test
    void renglonesSinMedicamentoSeIgnoranYNoSeRepite() {
        var hallazgos = LectorNotaQuimio.leer("Ca de mama\n7 ciclo\nDEXAMETASONA 16 MG IV\nDEXAMETASONA 8 MG VO", CATALOGO);
        assertThat(hallazgos).hasSize(1);
        assertThat(LectorNotaQuimio.leer(null, CATALOGO)).isEmpty();
    }

    @Test
    void cloruroDeSodioNoSaleTambienComoSodioYUnaFormaNoEsFarmaco() {
        var hallazgos = LectorNotaQuimio.leer("Cloruro de sodio 0.9% 100 ml p/ 10 min\nNETUPITANT/ PALONOSETRON 300 MG 1 TABLETA VIA ORAL", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("CLORURO SODIO");
        assertThat(LectorNotaQuimio.palabrasFarmaco("Tableta Cada tableta contiene")).isEmpty();
    }

    @Test
    void nombresConUnaFaltaDeOrtografia() {
        var hallazgos = LectorNotaQuimio.leer("Defenhidramina 25 mg en 100 ml\nPembrolizimab 200 mg en 250 ml\nVINCRSITINA 2MG IV EN BOLO", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("DIFENHIDRAMINA", "PEMBROLIZUMAB", "VINCRISTINA");
        // Una errata solo en nombres largos: palabras cortas distintas no se confunden.
        assertThat(LectorNotaQuimio.parecida("SODIO", "YODO")).isFalse();
    }

    @Test
    void gramoYDosisEnElRenglonDeAbajoYMencionPreviaSinDosis() {
        var hallazgos = LectorNotaQuimio.leer("""
                MANTENIMIENTO CON 3RA DOSIS DE RITUXIMAB EL 21 DE NOVIEMBRE
                RITUXIMAB 600 MG EN 500CC DE S.S AL 0.9%
                PARACETAMOL 1 GRAMO IV
                Difenhidramina solución inyectable
                Administrar  50 mg vía intravenosa . DOSIS UNICA""", CATALOGO);
        assertThat(hallazgos).extracting(LectorNotaQuimio.Hallazgo::farmaco).containsExactly("RITUXIMAB", "PARACETAMOL", "DIFENHIDRAMINA");
        assertThat(hallazgos.get(0).dosisCantidad()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(hallazgos.get(1).dosisUnidad()).isEqualTo("g");
        assertThat(hallazgos.get(2).dosisCantidad()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void contenidoPorFrascoDeLaConcentracion() {
        BigDecimal[] cantidad = new BigDecimal[1];
        assertThat(LectorNotaQuimio.contenido("300 mg/50 ml", cantidad)).isEqualTo("mg");
        assertThat(cantidad[0]).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(LectorNotaQuimio.contenido("Cada frasco ámpula o ampolleta", new BigDecimal[1])).isNull();
    }
}
