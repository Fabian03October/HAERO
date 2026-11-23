package com.example.backend.almacen.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

import com.example.backend.almacen.entity.Lote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {
    List<Lote> findByMedicamentoClaveAndEstatus(String medicamentoClave, String estatus);

    List<Lote> findByEstatus(String estatus);

    List<Lote> findByLoteOrigenIsNotNull();

    boolean existsByMedicamentoClaveAndNumeroLoteIgnoreCase(String medicamentoClave, String numeroLote);

    List<Lote> findByMedicamentoClaveAndNumeroLoteIgnoreCaseAndEstatus(String medicamentoClave, String numeroLote, String estatus);

    // Trazabilidad de Farmacia (HU33): el lote buscado en cualquier clave.
    List<Lote> findByNumeroLoteIgnoreCase(String numeroLote);

    /** El lote con candado de escritura hasta que termine la transacción (dos personas no lo cambian a la vez). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id = :id")
    Optional<Lote> bloquear(Long id);
}
