package com.example.backend.usuarios.repository;

import com.example.backend.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
    boolean existsByNombreUsuario(String nombreUsuario);
    boolean existsByCorreo(String correo);
    boolean existsByCorreoAndIdNot(String correo, Long id);
    List<Usuario> findByNombreCompletoContainingIgnoreCase(String nombreCompleto);
}