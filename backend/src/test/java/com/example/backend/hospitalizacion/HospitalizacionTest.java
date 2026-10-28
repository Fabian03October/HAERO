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
}
