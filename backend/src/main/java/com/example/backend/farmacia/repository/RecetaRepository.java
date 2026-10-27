package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.Receta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecetaRepository extends JpaRepository<Receta, Long> {

    Optional<Receta> findByFolioIgnoreCase(String folio);

    List<Receta> findByEstadoInOrderByFechaDesc(Collection<String> estados);
}
