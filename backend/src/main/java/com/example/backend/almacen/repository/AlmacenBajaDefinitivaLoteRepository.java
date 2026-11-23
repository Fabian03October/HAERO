package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.AlmacenBajaDefinitivaLote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AlmacenBajaDefinitivaLoteRepository extends JpaRepository<AlmacenBajaDefinitivaLote, AlmacenBajaDefinitivaLote.Llave> {

    /** Los lotes de varias bajas con su lote y medicamento (una sola consulta para todo el listado). */
    @Query("""
            SELECT bl FROM AlmacenBajaDefinitivaLote bl JOIN FETCH bl.lote l JOIN FETCH l.medicamento m
            WHERE bl.bajaId IN :bajas ORDER BY m.clave, l.numeroLote
            """)
    List<AlmacenBajaDefinitivaLote> deLasBajas(Collection<Long> bajas);
}
