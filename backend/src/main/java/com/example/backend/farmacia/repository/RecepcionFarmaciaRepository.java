package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.RecepcionFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecepcionFarmaciaRepository extends JpaRepository<RecepcionFarmacia, Long> {

    List<RecepcionFarmacia> findAllByOrderByFechaDesc();
}
