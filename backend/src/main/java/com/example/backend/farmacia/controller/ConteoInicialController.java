package com.example.backend.farmacia.controller;

import com.example.backend.farmacia.service.ServicioConteoInicial;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Conteo del inventario inicial. Misma ruta bajo Almacén y bajo Farmacia, así cada rol solo
 * toca el suyo (SecurityConfig) y Supervisión puede consultarlo para el reporte (GET).
 */
@RestController
@RequestMapping({"/api/almacen/conteo-inicial", "/api/farmacia/conteo-inicial"})
public class ConteoInicialController {

    private final ServicioConteoInicial servicio;

    public ConteoInicialController(ServicioConteoInicial servicio) {
        this.servicio = servicio;
    }

    public record Cantidad(Integer cantidad) {
    }

    @GetMapping
    public ResponseEntity<ServicioConteoInicial.Estado> estado(HttpServletRequest request) {
        return ResponseEntity.ok(servicio.estado(area(request)));
    }

    /** Lotes de una clave que Intelisis tiene en existencia, para elegir lote y caducidad. */
    @GetMapping("/lotes")
    public ResponseEntity<java.util.List<ServicioConteoInicial.LoteIntelisis>> lotes(@RequestParam String clave) {
        return ResponseEntity.ok(servicio.lotesIntelisis(clave));
    }

    @PostMapping("/renglones")
    public ResponseEntity<ServicioConteoInicial.Renglon> agregar(@RequestBody ServicioConteoInicial.NuevoRenglon nuevo,
                                                                 HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.agregar(area(request), nuevo, authentication.getName()));
    }

    @PutMapping("/renglones/{id}/cantidad")
    public ResponseEntity<ServicioConteoInicial.Renglon> cantidad(@PathVariable long id, @RequestBody Cantidad cantidad,
                                                                  HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.capturarCantidad(area(request), id, cantidad.cantidad(), authentication.getName()));
    }

    public record Ubicacion(String ubicacion) {
    }

    /** Pasa el renglón a otro estante; si ahí ya estaba el mismo lote, se juntan y se suman las piezas. */
    @PutMapping("/renglones/{id}/ubicacion")
    public ResponseEntity<ServicioConteoInicial.Renglon> mover(@PathVariable long id, @RequestBody Ubicacion ubicacion,
                                                               HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.mover(area(request), id, ubicacion.ubicacion(), authentication.getName()));
    }

    public record CambioEstante(String actual, String nuevo) {
    }

    /** Cambia el nombre de un estante (todos sus renglones). Body: { actual, nuevo }; "" o null = sin estante. */
    @PutMapping("/estantes")
    public ResponseEntity<ServicioConteoInicial.Estado> renombrarEstante(@RequestBody CambioEstante cambio,
                                                                         HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.renombrarEstante(area(request), cambio.actual(), cambio.nuevo(), authentication.getName()));
    }

    @DeleteMapping("/renglones/{id}")
    public ResponseEntity<Void> quitar(@PathVariable long id, HttpServletRequest request) {
        servicio.quitar(area(request), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cerrar")
    public ResponseEntity<ServicioConteoInicial.Estado> cerrar(HttpServletRequest request, Authentication authentication) {
        return ResponseEntity.ok(servicio.cerrar(area(request), authentication.getName()));
    }

    private static String area(HttpServletRequest request) {
        return request.getRequestURI().contains("/api/farmacia/") ? ServicioConteoInicial.FARMACIA : ServicioConteoInicial.ALMACEN;
    }
}
