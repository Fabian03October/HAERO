package com.example.backend.hospitalizacion;

import com.example.backend.hospitalizacion.dto.AreaHospitalizacion;
import com.example.backend.hospitalizacion.dto.CamaHospitalizacion;
import com.example.backend.hospitalizacion.dto.DetalleCama;
import com.example.backend.hospitalizacion.dto.EstadoHospitalizacion;
import com.example.backend.hospitalizacion.dto.EventoHospitalizacion;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Hospitalización: camas por área, quién está en cada cama, sus medicamentos y novedades. */
@RestController
@RequestMapping("/api/hospitalizacion")
public class HospitalizacionController {

    private final ServicioHospitalizacion servicio;

    public HospitalizacionController(ServicioHospitalizacion servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/areas")
    public ResponseEntity<List<AreaHospitalizacion>> areas(Authentication authentication) {
        return ResponseEntity.ok(servicio.areas(authentication.getName()));
    }

    @GetMapping("/areas/{central}/camas")
    public ResponseEntity<List<CamaHospitalizacion>> camas(@PathVariable String central, Authentication authentication) {
        return ResponseEntity.ok(servicio.camas(central, authentication.getName()));
    }

    @GetMapping("/camas/{espacio}")
    public ResponseEntity<DetalleCama> detalle(@PathVariable String espacio, Authentication authentication) {
        return ResponseEntity.ok(servicio.detalle(espacio, authentication.getName()));
    }

    @PostMapping("/camas/{espacio}/visto")
    public ResponseEntity<Void> marcarVisto(@PathVariable String espacio, Authentication authentication) {
        servicio.marcarVisto(espacio, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/novedades")
    public ResponseEntity<List<EventoHospitalizacion>> novedades(@RequestParam(required = false) String central, Authentication authentication) {
        return ResponseEntity.ok(servicio.novedades(central, authentication.getName()));
    }

    @GetMapping("/estado")
    public ResponseEntity<EstadoHospitalizacion> estado(Authentication authentication) {
        return ResponseEntity.ok(servicio.estado(authentication.getName()));
    }
}
