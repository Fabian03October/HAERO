package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.LoteFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoteFarmaciaRepository extends JpaRepository<LoteFarmacia, Long> {

    Optional<LoteFarmacia> findByMedicamentoClaveAndNumeroLoteIgnoreCase(String clave, String numeroLote);

    List<LoteFarmacia> findByMedicamentoClave(String clave);

    List<LoteFarmacia> findByNumeroLoteIgnoreCase(String numeroLote);

    /** Todo el inventario con su medicamento en una sola consulta. */
    @Query("SELECT l FROM LoteFarmacia l JOIN FETCH l.medicamento ORDER BY l.medicamento.clave, l.caducidad, l.id")
    List<LoteFarmacia> findAllConMedicamento();
}
