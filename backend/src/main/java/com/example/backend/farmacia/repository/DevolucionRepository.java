package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    List<Devolucion> findByDiaResumen(LocalDate dia);

    List<Devolucion> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}
