package com.example.backend.farmacia.service;

import com.example.backend.farmacia.dto.HojaBajaResponse;
import com.example.backend.farmacia.entity.FarmaciaBajaVolumen;
import com.example.backend.farmacia.entity.FarmaciaHojaBaja;
import com.example.backend.farmacia.entity.Receta;
import com.example.backend.farmacia.repository.EntregaRepository;
import com.example.backend.farmacia.repository.FarmaciaBajaVolumenRepository;
import com.example.backend.farmacia.repository.FarmaciaHojaBajaRepository;
import com.example.backend.usuarios.entity.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Baja por volumen de Quimioterapia y de Quirófano, igual que la de Hospitalización: en Intelisis se
 * captura UNA baja por área y día (totales por clave y lote) y el detalle por paciente queda en HAERO.
 * "Terminar el día" le pone hoja a cada paciente con algo entregado hoy y suma lo que todavía no
 * estaba en la baja del día (si se termina otra vez, se suma a la misma, con el mismo folio).
 * Se imprimen con las mismas hojas que Hospitalización (central = QUIMIO o QUIROFANO).
 */
@Service
public class ServicioBajaArea {

    // Área → prefijo del folio de las recetas que genera.
    static final Map<String, String> AREAS = Map.of(
            "QUIMIO", ServicioQuimioterapia.PREFIJO_FOLIO,
            "QUIROFANO", ServicioCajasQuirofano.PREFIJO_FOLIO);

    private final EntregaRepository entregas;
    private final FarmaciaBajaVolumenRepository bajasVolumen;
    private final FarmaciaHojaBajaRepository hojasBaja;
    private final ServicioHojasCarrito hojas;
    private final ServicioInventarioFarmacia inventario;

    public ServicioBajaArea(EntregaRepository entregas, FarmaciaBajaVolumenRepository bajasVolumen, FarmaciaHojaBajaRepository hojasBaja,
                            ServicioHojasCarrito hojas, ServicioInventarioFarmacia inventario) {
        this.entregas = entregas;
        this.bajasVolumen = bajasVolumen;
        this.hojasBaja = hojasBaja;
        this.hojas = hojas;
        this.inventario = inventario;
    }

    public record EstadoBajaArea(String area, LocalDate dia, int pacientesPendientes, int entregasPendientes, Integer folioDelDia,
                                 LocalDateTime ultimaBaja, String ultimaBajaPor) {
    }

    /** Lo que falta de pasar a la baja del día y la baja que ya existe (si hay). */
    @Transactional(readOnly = true)
    public EstadoBajaArea estado(String area) {
        String prefijo = prefijo(area);
        LocalDate hoy = LocalDate.now();
        EntregaRepository.Pendientes pendientes = entregas.pendientesDeBaja(prefijo + "%", hoy.plusDays(1).atStartOfDay());
        FarmaciaBajaVolumen baja = bajasVolumen.findFirstByDiaAndCentralOrderByFolio(hoy, area).orElse(null);
        return new EstadoBajaArea(area, hoy, (int) pendientes.getPacientes(), (int) pendientes.getEntregas(),
                baja == null ? null : baja.getFolio(), baja == null ? null : baja.getFecha(),
                baja == null ? null : baja.getUsuario().getNombreCompleto());
    }

    @Transactional
    public List<HojaBajaResponse> terminar(String area, String nombreUsuario) {
        String prefijo = prefijo(area);
        Usuario usuario = inventario.usuario(nombreUsuario);
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        // Hoja por paciente (receta) con algo entregado que todavía no tiene. También lo de días
        // anteriores que no se pasó (por ejemplo, la caja del turno nocturno que regresó al otro día):
        // entra en la baja de HOY, que es cuando se captura en Intelisis; nada se queda sin baja.
        for (Receta receta : entregas.recetasSinHoja(prefijo + "%", hoy.plusDays(1).atStartOfDay())) {
            FarmaciaHojaBaja hoja = new FarmaciaHojaBaja();
            hoja.setReceta(receta);
            hoja.setDia(hoy);
            hoja.setCentral(area);
            hoja.setFecha(ahora);
            hoja.setUsuario(usuario);
            hojasBaja.save(hoja);
        }
        hojasBaja.flush();

        if (entregas.sinBajaConHoja(area) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay entregas pendientes para dar de baja");
        }
        // Si ya hubo baja hoy, se suma a la misma (mismo folio).
        FarmaciaBajaVolumen baja = bajasVolumen.findFirstByDiaAndCentralOrderByFolio(hoy, area).orElseGet(() -> {
            FarmaciaBajaVolumen nueva = new FarmaciaBajaVolumen();
            nueva.setDia(hoy);
            nueva.setCentral(area);
            return nueva;
        });
        baja.setFecha(ahora);
        baja.setUsuario(usuario);
        bajasVolumen.saveAndFlush(baja);
        entregas.asignarBajaVolumen(baja.getFolio(), area);
        return hojas.bajasVolumen(area, hoy, baja.getFolio());
    }

    private static String prefijo(String area) {
        String prefijo = area == null ? null : AREAS.get(area);
        if (prefijo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El área debe ser QUIMIO o QUIROFANO");
        }
        return prefijo;
    }
}
