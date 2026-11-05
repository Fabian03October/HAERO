package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.MedicamentoSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicamentoSeguridadRepository extends JpaRepository<MedicamentoSeguridad, String> {
}
