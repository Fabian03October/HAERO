package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.ConteoInicial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Cierre del inventario inicial por área (ALMACEN o FARMACIA): si existe, el área ya lo guardó. */
public interface ConteoInicialRepository extends JpaRepository<ConteoInicial, String> {

    @EntityGraph(attributePaths = "cerradoPor")
    Optional<ConteoInicial> findByArea(String area);
}
