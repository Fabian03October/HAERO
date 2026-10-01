package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);

    List<Movimiento> findByExistenciaLoteIdAndTipo(Long loteId, String tipo);

    List<Movimiento> findByTipoInOrderByFechaDesc(List<String> tipos);
}
