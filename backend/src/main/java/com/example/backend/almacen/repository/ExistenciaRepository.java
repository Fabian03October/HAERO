package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Existencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExistenciaRepository extends JpaRepository<Existencia, Long> {
    List<Existencia> findByLoteId(Long loteId);

    /**
     * Todas las existencias con su lote, medicamento y pedido en una sola consulta.
     * Con findAll() Hibernate traía cada lote, medicamento y pedido por separado (una
     * consulta por renglón), lo que con el inventario completo volvía lentas las pantallas.
     */
    @Query("SELECT e FROM Existencia e JOIN FETCH e.lote l JOIN FETCH l.medicamento "
            + "LEFT JOIN FETCH l.pedido p LEFT JOIN FETCH p.usuarioCancelacion LEFT JOIN FETCH l.loteOrigen")
    List<Existencia> findAllConLote();
}
