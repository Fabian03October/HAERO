package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.MovimientoFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoFarmaciaRepository extends JpaRepository<MovimientoFarmacia, Long> {

    List<MovimientoFarmacia> findByLoteIdInOrderByFechaAsc(List<Long> lotes);
}
