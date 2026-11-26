package com.example.backend.intelisis.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NombrePacienteTest {

    @Test
    void conCurpAcomodaLosApellidosDespuesDelNombre() {
        // GALM = García, López, María
        assertThat(NombrePaciente.ordenar("GARCIA LOPEZ MARIA", "GALM800101MOCRPR09")).isEqualTo("MARIA GARCIA LOPEZ");
        assertThat(NombrePaciente.ordenar("GARCIA LOPEZ MARIA JOSE", "GALM800101MOCRPR09")).isEqualTo("MARIA JOSE GARCIA LOPEZ");
    }

    @Test
    void siYaVieneBienOSeNoSePuedeSaberSeDejaIgual() {
        assertThat(NombrePaciente.ordenar("MARIA GARCIA LOPEZ", "GALM800101MOCRPR09")).isEqualTo("MARIA GARCIA LOPEZ");
        assertThat(NombrePaciente.ordenar("GARCIA LOPEZ MARIA", null)).isEqualTo("GARCIA LOPEZ MARIA");
        assertThat(NombrePaciente.ordenar("PEREZ JUAN", "PEXJ800101HOCRXN09")).isEqualTo("PEREZ JUAN");
        // Ambas lecturas cuadran (iniciales repetidas): no se adivina.
        assertThat(NombrePaciente.ordenar("LUNA LOPEZ LUIS", "LULL800101HOCNPS09")).isEqualTo("LUNA LOPEZ LUIS");
    }

    @Test
    void laMismaPersonaEnCualquierOrdenDaLaMismaLlave() {
        assertThat(NombrePaciente.llave("GARCIA LOPEZ MARIA")).isEqualTo(NombrePaciente.llave("María  García López"));
        assertThat(NombrePaciente.llave("ANA MARIA GARCIA")).isNotEqualTo(NombrePaciente.llave("MARIA GARCIA"));
        assertThat(NombrePaciente.contiene("GARCIA LOPEZ MARIA", "maria garcía")).isTrue();
        assertThat(NombrePaciente.contiene("GARCIA LOPEZ MARIA", "maria perez")).isFalse();
        assertThat(NombrePaciente.contiene(null, "maria")).isFalse();
    }

    @Test
    void encuentraAlPacienteEnCualquierOrdenYConMenosPalabras() {
        String buscado = "fabian jimenez castillejos";
        assertThat(NombrePaciente.contiene("FABIAN JIMENEZ CASTILLEJOS", buscado)).isTrue();
        assertThat(NombrePaciente.contiene("JIMENEZ CASTILLEJOS FABIAN", buscado)).isTrue();
        assertThat(NombrePaciente.contiene("FABIAN JIMÉNEZ", buscado)).isTrue();
        assertThat(NombrePaciente.contiene("JIMENEZ FABIAN", buscado)).isTrue();
        assertThat(NombrePaciente.contiene("FABIAN", buscado)).isFalse();
        assertThat(NombrePaciente.contiene("FABIAN LOPEZ", buscado)).isFalse();
        assertThat(NombrePaciente.contiene("JIMENEZ CASTILLEJOS FABIAN", "jimenez fabian")).isTrue();
        assertThat(NombrePaciente.pares(buscado)).hasSize(3);
    }
}
