package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    List<Lote> findByMedicamentoClaveAndEstatus(String medicamentoClave, String estatus);
}
