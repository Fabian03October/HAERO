package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.Entrega;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    List<Entrega> findByRecetaIdOrderByFechaAsc(Long recetaId);

    List<Entrega> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);

    List<Entrega> findTop20ByRecetaPacienteExpedienteOrderByFechaDesc(String expediente);
}
