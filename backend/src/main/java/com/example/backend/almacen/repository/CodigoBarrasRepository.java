package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.CodigoBarras;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoBarrasRepository extends JpaRepository<CodigoBarras, String> {
}
