package com.example.backend.repository;

import com.example.backend.entity.BitacoraUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BitacoraUsuarioRepository extends JpaRepository<BitacoraUsuario, Long> {

    List<BitacoraUsuario> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);

    List<BitacoraUsuario> findByUsuarioIdAndFechaBetweenOrderByFechaDesc(Long usuarioId, LocalDateTime desde, LocalDateTime hasta);
}
