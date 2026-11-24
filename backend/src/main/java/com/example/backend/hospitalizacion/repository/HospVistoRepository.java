package com.example.backend.hospitalizacion.repository;

import com.example.backend.hospitalizacion.entity.HospVisto;
import org.springframework.data.jpa.repository.JpaRepository;

/** Hasta cuándo vio cada usuario las novedades de cada cama. */
public interface HospVistoRepository extends JpaRepository<HospVisto, HospVisto.Llave> {
}
