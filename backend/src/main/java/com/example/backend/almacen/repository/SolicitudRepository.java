package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findByEstatusIn(List<String> estatus);
    List<Solicitud> findAllByOrderByFechaDesc();
}
