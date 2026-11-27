package com.example.backend.farmacia.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas del script de almacén (Ada) para la fuente de financiamiento, sin Intelisis. */
class ServicioFinanciamientoIntelisisTest {

    @Test
    void ordenYRemisionSalenDeCaracteristicasPrimero() {
        var d = ServicioFinanciamientoIntelisis.datos("12345678", null, null,
                Map.of("ORDEN DE REPOSICION", "OS-SAEAC-2025-001", "NUMERO DE REMISION", "R-77"));
        assertEquals("OS-SAEAC-2025-001", d.ordenSuministro());
        assertEquals("R-77", d.remision());
        assertEquals("CARACTERISTICAS", d.origenRemision());
    }

    @Test
    void sinCaracteristicasSeUsanAnexoYReferencia() {
        // Anexo "ORD_X-SADMI-2025-14-1234567": la remisión es lo que va después del último guion (7+ dígitos).
        var d = ServicioFinanciamientoIntelisis.datos("123456", null, "ORD_X-SADMI-2025-14-1234567", Map.of());
        assertEquals("1234567", d.remision());
        assertEquals("ARCHIVO", d.origenRemision());
        assertEquals("SADMI-2025-14", d.ordenSuministro());
        // Referencia con texto = orden; "REM: 998 ..." en el proveedor = remisión.
        var r = ServicioFinanciamientoIntelisis.datos("SFAS/2025/9", "LAB X REM: 998 OTRO", null, Map.of());
        assertEquals("SFAS/2025/9", r.ordenSuministro());
        assertEquals("998", r.remision());
        assertEquals("REFERENCIA", r.origenRemision());
    }

    @Test
    void fuenteYCartaCanje() {
        assertEquals("SAEAC", ServicioFinanciamientoIntelisis.fuente("E003-14", "L1"));
        assertEquals("SAEPGC", ServicioFinanciamientoIntelisis.fuente(null, "C25F101 SAPG"));
        assertEquals("SIN FUENTE", ServicioFinanciamientoIntelisis.fuente("123", "C25F101"));
        LocalDate llegada = LocalDate.of(2025, 1, 10);
        assertEquals("SCC", ServicioFinanciamientoIntelisis.cartaCanje("L1", LocalDate.of(2026, 6, 1), llegada));
        assertEquals("CCC", ServicioFinanciamientoIntelisis.cartaCanje("L1", LocalDate.of(2025, 9, 1), llegada));
        assertEquals("CCC", ServicioFinanciamientoIntelisis.cartaCanje("L1 CCC", LocalDate.of(2030, 1, 1), llegada));
    }

    @Test
    void cadaConsumoTocaALaEntradaDondeCaeSuPrimeraPiezaPorPeps() {
        String art = "010.000.2248.00-1";
        // Dos entregas del mismo lote: la que caduca antes va primero aunque haya llegado después.
        var tarde = mov(art, 2, "1500", LocalDateTime.of(2025, 3, 1, 9, 0), LocalDate.of(2026, 12, 31), 10, "SAEAC-1");
        var pronto = mov(art, 1, "1400", LocalDateTime.of(2025, 4, 1, 9, 0), LocalDate.of(2026, 6, 30), 5, "SADMI-2");
        var entradas = ServicioFinanciamientoIntelisis.armarEntradas(List.of(tarde, pronto), Map.of(), Map.of());
        assertEquals("Donacion FONSABI 1400", entradas.get(0).entrada());
        assertEquals(1, entradas.get(0).piezaDesde());
        assertEquals(5, entradas.get(0).piezaHasta());
        assertEquals(15, entradas.get(1).piezaHasta());
        assertEquals("SADMI", entradas.get(0).fuente());

        var consumos = List.of(
                new ServicioFinanciamientoIntelisis.Consumo(art, "C25F101", "Consumo Farmacia 1", BigDecimal.valueOf(4)),
                new ServicioFinanciamientoIntelisis.Consumo(art, "C25F101", "Consumo Farmacia 2", BigDecimal.valueOf(3)),
                new ServicioFinanciamientoIntelisis.Consumo(art, "C25F101", "Consumo Farmacia 3", BigDecimal.valueOf(20)),
                new ServicioFinanciamientoIntelisis.Consumo(art, "C25F101", "Consumo Farmacia 4", BigDecimal.ONE));
        var toca = ServicioFinanciamientoIntelisis.repartirPeps(entradas, consumos);
        String clave = "010.000.2248.00";
        assertEquals("Donacion FONSABI 1400", toca.get(ServicioFinanciamientoIntelisis.llaveConsumo("Consumo Farmacia 1", clave, "C25F101")).entrada());
        // Ya van 4: la 5a pieza todavía es de la 1400.
        assertEquals("Donacion FONSABI 1400", toca.get(ServicioFinanciamientoIntelisis.llaveConsumo("Consumo Farmacia 2", clave, "C25F101")).entrada());
        assertEquals("Donacion FONSABI 1500", toca.get(ServicioFinanciamientoIntelisis.llaveConsumo("Consumo Farmacia 3", clave, "C25F101")).entrada());
        // Se consumió más de lo que entró: a la última entrada.
        assertEquals("Donacion FONSABI 1500", toca.get(ServicioFinanciamientoIntelisis.llaveConsumo("Consumo Farmacia 4", clave, "C25F101")).entrada());
    }

    private static ServicioFinanciamientoIntelisis.Movimiento mov(String art, long id, String movId, LocalDateTime llegada,
                                                                   LocalDate caducidad, int piezas, String referencia) {
        return new ServicioFinanciamientoIntelisis.Movimiento(art, "C25F101", id, "Donacion FONSABI", movId, "HOSPITALARIA",
                referencia, "LAB", llegada, llegada, BigDecimal.valueOf(piezas), BigDecimal.TEN, null, caducidad, null);
    }
}
