package com.example.backend.controller;

import com.example.backend.dto.CambiarContrasenaRequest;
import com.example.backend.dto.CrearUsuarioRequest;
import com.example.backend.dto.EditarUsuarioRequest;
import com.example.backend.dto.RestablecerContrasenaRequest;
import com.example.backend.dto.UsuarioResponse;
import com.example.backend.service.ServicioUsuarios;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = servicioUsuarios.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> editar(@PathVariable Long id, @RequestBody EditarUsuarioRequest request) {
        return ResponseEntity.ok(servicioUsuarios.editarUsuario(id, request));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioResponse> desactivar(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(servicioUsuarios.desactivarUsuario(id, authentication.getName()));
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<UsuarioResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(servicioUsuarios.reactivarUsuario(id));
    }

    @PatchMapping("/{id}/restablecer-contrasena")
    public ResponseEntity<UsuarioResponse> restablecerContrasena(@PathVariable Long id,
                                                                  @RequestBody RestablecerContrasenaRequest request) {
        return ResponseEntity.ok(servicioUsuarios.restablecerContrasena(id, request));
    }

    @PutMapping("/me/contrasena")
    public ResponseEntity<Void> cambiarMiContrasena(@RequestBody CambiarContrasenaRequest request,
                                                     Authentication authentication) {
        servicioUsuarios.cambiarContrasenaPropia(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
