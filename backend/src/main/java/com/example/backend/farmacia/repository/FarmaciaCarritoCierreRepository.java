package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.FarmaciaCarritoCierre;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

/** Último cierre del carrito de un área en un día (lo marca "Terminar carrito"). */
public interface FarmaciaCarritoCierreRepository extends JpaRepository<FarmaciaCarritoCierre, FarmaciaCarritoCierre.Llave> {

    @EntityGraph(attributePaths = "usuario")
    Optional<FarmaciaCarritoCierre> findByCentralAndDia(String central, LocalDate dia);
}
