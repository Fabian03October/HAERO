package com.example.backend.controller;

import com.example.backend.dto.AceptacionPoliticasResponse;
import com.example.backend.dto.AceptarPoliticasRequest;
import com.example.backend.dto.BitacoraUsuarioResponse;
import com.example.backend.dto.CambiarContrasenaRequest;
import com.example.backend.dto.CrearUsuarioRequest;
import com.example.backend.dto.EditarUsuarioRequest;
import com.example.backend.dto.RestablecerContrasenaRequest;
import com.example.backend.dto.UsuarioResponse;
import com.example.backend.service.ServicioUsuarios;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final ServicioUsuarios servicioUsuarios;

    public UsuarioController(ServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar(@RequestParam(required = false) String nombre) {
        return ResponseEntity.ok(servicioUsuarios.listarUsuarios(nombre));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(servicioUsuarios.obtenerUsuario(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@RequestBody CrearUsuarioRequest request, Authentication authentication) {
        UsuarioResponse response = servicioUsuarios.crearUsuario(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> editar(@PathVariable Long id, @RequestBody EditarUsuarioRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.editarUsuario(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioResponse> desactivar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.desactivarUsuario(id, authentication.getName()));
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<UsuarioResponse> reactivar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.reactivarUsuario(id, authentication.getName()));
    }

    @PatchMapping("/{id}/restablecer-contrasena")
    public ResponseEntity<UsuarioResponse> restablecerContrasena(@PathVariable Long id,
                                                                  @RequestBody RestablecerContrasenaRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.restablecerContrasena(id, request, authentication.getName()));
    }

    // HU41: quién hizo qué con cada cuenta. Por defecto, los últimos 30 días.
    @GetMapping("/bitacora")
    public ResponseEntity<List<BitacoraUsuarioResponse>> bitacora(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicioUsuarios.bitacora(usuarioId, desde, hasta));
    }

    @PutMapping("/me/contrasena")
    public ResponseEntity<Void> cambiarMiContrasena(@RequestBody CambiarContrasenaRequest request,
                                                     Authentication authentication) {
        servicioUsuarios.cambiarContrasenaPropia(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/politicas")
    public ResponseEntity<AceptacionPoliticasResponse> aceptarPoliticas(@RequestBody AceptarPoliticasRequest request,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.aceptarPoliticas(authentication.getName(), request));
    }
}
