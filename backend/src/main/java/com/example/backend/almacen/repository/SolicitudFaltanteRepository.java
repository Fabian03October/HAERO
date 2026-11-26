package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.SolicitudFaltante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SolicitudFaltanteRepository extends JpaRepository<SolicitudFaltante, Long> {

    Optional<SolicitudFaltante> findFirstBySolicitudIdOrderByIdDesc(Long solicitudId);
}
