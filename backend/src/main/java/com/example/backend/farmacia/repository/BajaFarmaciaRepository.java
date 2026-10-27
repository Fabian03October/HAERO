package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.BajaFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BajaFarmaciaRepository extends JpaRepository<BajaFarmacia, Long> {

    List<BajaFarmacia> findAllByOrderByFechaDesc();

    List<BajaFarmacia> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);
}
