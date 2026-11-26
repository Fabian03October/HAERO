package com.example.backend.farmacia.service;

import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.PartidaEntrega;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicioTrazabilidadPeriodoTest {

    private static PartidaEntrega partida(String fecha) {
        Entrega entrega = new Entrega();
        entrega.setFecha(LocalDateTime.parse(fecha));
        PartidaEntrega partida = new PartidaEntrega();
        partida.setEntrega(entrega);
        return partida;
    }

    @Test
    void incluyeLosDiasCompletosDeInicioYFin() {
        var periodo = ServicioTrazabilidad.Periodo.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 24));
        List<PartidaEntrega> todas = List.of(partida("2026-10-31T23:59:00"), partida("2026-11-01T00:00:00"),
                partida("2026-11-24T23:59:00"), partida("2026-11-25T00:00:00"));
        assertEquals(List.of(todas.get(1), todas.get(2)), periodo.filtrar(todas));
    }

    @Test
    void sinFechasNoFiltraNada() {
        List<PartidaEntrega> todas = List.of(partida("2020-01-01T10:00:00"));
        assertEquals(todas, ServicioTrazabilidad.Periodo.de(null, null).filtrar(todas));
        assertNull(ServicioTrazabilidad.Periodo.de(null, null).antesDe(null));
    }

    @Test
    void fechaFinalAntesDeLaInicialEsError() {
        assertThrows(ResponseStatusException.class,
                () -> ServicioTrazabilidad.Periodo.de(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void intelisisSeCortaEnElFinDelPeriodoOEnElInicioDeHaero() {
        LocalDateTime inicioHaero = LocalDateTime.of(2026, 11, 10, 8, 0);
        var octubre = ServicioTrazabilidad.Periodo.de(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), octubre.antesDe(inicioHaero));
        var noviembre = ServicioTrazabilidad.Periodo.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertEquals(inicioHaero, noviembre.antesDe(inicioHaero));
    }
}
