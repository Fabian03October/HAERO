package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.Resurtimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResurtimientoRepository extends JpaRepository<Resurtimiento, Long> {

    long countByRecetaId(Long recetaId);

    Optional<Resurtimiento> findFirstByRecetaIdOrderByFechaDesc(Long recetaId);
}
