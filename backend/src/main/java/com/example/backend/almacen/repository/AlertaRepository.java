package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Optional<Alerta> findByMedicamentoClaveAndTipoAndFechaFinIsNull(String medicamentoClave, String tipo);

    Optional<Alerta> findByLoteIdAndTipoAndFechaFinIsNull(Long loteId, String tipo);

    List<Alerta> findByFechaFinIsNullOrderByFechaInicioDesc();

    List<Alerta> findByFechaFinIsNotNullOrderByFechaFinDesc();
}
