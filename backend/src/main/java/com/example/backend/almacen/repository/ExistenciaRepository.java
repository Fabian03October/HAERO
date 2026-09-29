package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Existencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExistenciaRepository extends JpaRepository<Existencia, Long> {
    List<Existencia> findByLoteId(Long loteId);
}
