package com.example.backend.hospitalizacion.repository;

import com.example.backend.hospitalizacion.entity.HospConsulta;
import org.springframework.data.jpa.repository.JpaRepository;

/** Quién vio los datos de qué cama (privacidad de Hospitalización). */
public interface HospConsultaRepository extends JpaRepository<HospConsulta, Long> {
}
