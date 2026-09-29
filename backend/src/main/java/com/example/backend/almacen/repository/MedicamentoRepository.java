package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Medicamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicamentoRepository extends JpaRepository<Medicamento, String> {
}
