package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.PartidaEntrega;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PartidaEntregaRepository extends JpaRepository<PartidaEntrega, Long> {

    List<PartidaEntrega> findByLoteIdIn(Collection<Long> lotes);

    List<PartidaEntrega> findByEntregaRecetaPacienteExpedienteOrderByEntregaFechaDesc(String expediente);
}
