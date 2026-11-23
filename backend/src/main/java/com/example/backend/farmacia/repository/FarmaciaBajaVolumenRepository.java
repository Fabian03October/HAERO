package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.FarmaciaBajaVolumen;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FarmaciaBajaVolumenRepository extends JpaRepository<FarmaciaBajaVolumen, Integer> {

    /** La baja por volumen del día de un área (central), con quién la hizo. */
    @EntityGraph(attributePaths = "usuario")
    Optional<FarmaciaBajaVolumen> findFirstByDiaAndCentralOrderByFolio(LocalDate dia, String central);
}
