package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.ExpedienteExterno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpedienteExternoRepository extends JpaRepository<ExpedienteExterno, Long> {

    List<ExpedienteExterno> findAllByOrderByFechaRegistroDesc();
}
