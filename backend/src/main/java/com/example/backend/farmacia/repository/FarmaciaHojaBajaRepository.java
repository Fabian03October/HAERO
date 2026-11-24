package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.FarmaciaHojaBaja;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmaciaHojaBajaRepository extends JpaRepository<FarmaciaHojaBaja, Integer> {

    boolean existsByRecetaId(Long recetaId);
}
