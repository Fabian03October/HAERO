package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.CodigoBarras;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CodigoBarrasRepository extends JpaRepository<CodigoBarras, String> {

    /** Cuantos codigos de barras tiene cada clave: [clave, total]. Una clave con codigo esta dada de alta en HAERO. */
    @Query("SELECT c.medicamento.clave, COUNT(c) FROM CodigoBarras c GROUP BY c.medicamento.clave")
    List<Object[]> contarPorClave();

    boolean existsByMedicamentoClave(String clave);
}
