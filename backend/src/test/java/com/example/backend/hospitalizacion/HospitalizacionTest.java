package com.example.backend.hospitalizacion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hospitalización: limpieza del nombre que trae Intelisis, descripción del medicamento y
 * orden natural de las camas.
 */
class HospitalizacionTest {

    @Test
    void elNombreDeIntelisisQuedaSinClaveNiExistencia() {
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre(
                "010.000.1768.00 - VINCRISTINA SOLUCION INYECTABLE, 1 MG - DISPONIBLE: 5"))
                .isEqualTo("VINCRISTINA SOLUCION INYECTABLE, 1 MG");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre("G-010.000.4158.00 - INSULINA GLARGINA - DISPONIBLE: 0"))
                .isEqualTo("INSULINA GLARGINA");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre("010.000.FCB.09 - MORFINA TABLETA, 30 MG, ORAL"))
                .isEqualTo("MORFINA TABLETA, 30 MG, ORAL");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre("010.000.0406.00U - DIFENHIDRAMINA 100 MG. AMPULA"))
                .isEqualTo("DIFENHIDRAMINA 100 MG. AMPULA");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre("Cloruro de sodio Solucion inyectable, 0.9 g/ 100 ml - Disponible: 0"))
                .isEqualTo("Cloruro de sodio Solucion inyectable, 0.9 g/ 100 ml");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre("PARACETAMOL")).isEqualTo("PARACETAMOL");
        assertThat(ServicioSincronizacionHospitalizacion.limpiarNombre(null)).isNull();
    }

    @Test
    void laDescripcionJuntaDosisViaYFrecuencia() {
        var m = new ServicioSincronizacionHospitalizacion.Medicamento(1, 1, 1, "010.000.0109.00", "METAMIZOL SODICO",
                "INTRAVENOSA", 1.0f, "GRAMO", 8, "horas", null, true, null);
        assertThat(m.descripcion()).isEqualTo("METAMIZOL SODICO 1 GRAMO INTRAVENOSA c/8 horas");

        var sinDatos = new ServicioSincronizacionHospitalizacion.Medicamento(2, 1, 1, "010.000.0104.00", null,
                null, null, null, null, null, null, true, null);
        assertThat(sinDatos.descripcion()).isEqualTo("010.000.0104.00");
    }

    @Test
    void lasCamasSeOrdenanComoSeLeen() {
        List<String> camas = new ArrayList<>(List.of("Hclin-U310", "Hclin-U32", "Hclin-AIS5", "Hclin-U4"));
        camas.sort(ServicioHospitalizacion::compararNatural);
        assertThat(camas).containsExactly("Hclin-AIS5", "Hclin-U4", "Hclin-U32", "Hclin-U310");
    }

    // ---------- Estancias que Intelisis dejó abiertas ----------

    private static ServicioSincronizacionHospitalizacion.Estancia estancia(int idArea, String cama, java.time.LocalDateTime ingreso) {
        return new ServicioSincronizacionHospitalizacion.Estancia(idArea, idArea * 10, cama, null, ingreso, null, "Paciente " + idArea, null);
    }

    @org.junit.jupiter.api.Test
    void unaEstanciaTapadaPorOtroPacienteTerminaCuandoEntraElSiguiente() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.of(2026, 10, 29, 12, 0);
        java.util.Map<Integer, ServicioSincronizacionHospitalizacion.Estancia> estancias = new java.util.LinkedHashMap<>();
        estancias.put(1, estancia(1, "205", ahora.minusDays(9)));      // Juan, desde el 20/10
        estancias.put(2, estancia(2, "205", ahora.minusHours(2)));     // Fabián entró hoy
        estancias.put(3, estancia(3, "310", ahora.minusDays(2000)));   // fantasma: nadie más después
        estancias.put(4, estancia(4, "311", ahora.minusDays(3)));      // normal

        var d = ServicioSincronizacionHospitalizacion.depurar(estancias, ahora, 365);

        org.assertj.core.api.Assertions.assertThat(d.vigentes()).containsExactlyInAnyOrder(2, 4);
        org.assertj.core.api.Assertions.assertThat(d.tapadas()).containsEntry(1, ahora.minusHours(2));
        org.assertj.core.api.Assertions.assertThat(d.fantasmas()).containsExactly(3);
    }
}
