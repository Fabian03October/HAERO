package com.example.backend.farmacia.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServicioTrazabilidadLoteTest {

    @Test
    void separaElSufijoDeIntelisis() {
        assertEquals("6020174", ServicioTrazabilidad.loteBase("6020174 SCC"));
        assertEquals("SCC", ServicioTrazabilidad.sufijoLote("6020174 SCC"));
        assertEquals("A123", ServicioTrazabilidad.loteBase(" A123 UNOPS "));
        assertEquals("F226C012", ServicioTrazabilidad.loteBase("F226C012/SADMI/SCC"));
        assertEquals("SADMI/SCC", ServicioTrazabilidad.sufijoLote("F226C012/SADMI/SCC"));
        assertEquals("1L26073", ServicioTrazabilidad.loteBase("1L26073 SCC SAEAC"));
        assertEquals("SCC SAEAC", ServicioTrazabilidad.sufijoLote("1L26073 SCC SAEAC"));
    }

    @Test
    void loteSinSufijoQuedaIgual() {
        assertEquals("LAC-2028-B", ServicioTrazabilidad.loteBase("LAC-2028-B"));
        assertNull(ServicioTrazabilidad.sufijoLote("LAC-2028-B"));
    }

    @Test
    void claveDeFarmaciaSinGuionUno() {
        assertEquals("010.000.0104.00", ServicioTrazabilidad.claveBase("010.000.0104.00-1"));
        assertEquals("010.000.0104.00", ServicioTrazabilidad.claveBase("010.000.0104.00"));
    }
}
