package com.example.backend.farmacia.repository;

import com.example.backend.almacen.entity.Movimiento;
import com.example.backend.farmacia.entity.PartidaRecepcionFarmacia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PartidaRecepcionFarmaciaRepository extends JpaRepository<PartidaRecepcionFarmacia, Long> {

    @Query("SELECT p.movimiento.id FROM PartidaRecepcionFarmacia p WHERE p.movimiento.id IN :movimientos")
    Set<Long> movimientosYaRecibidos(Collection<Long> movimientos);

    List<PartidaRecepcionFarmacia> findByLoteIdIn(Collection<Long> lotes);

    /** Salidas de Almacén a solicitudes de Farmacia que Farmacia todavía no recibe (HU26 CA1). */
    @Query("SELECT m FROM Movimiento m JOIN FETCH m.existencia e JOIN FETCH e.lote l JOIN FETCH l.medicamento "
            + "JOIN FETCH m.solicitud s JOIN FETCH m.usuario "
            + "WHERE m.tipo = 'SALIDA' AND NOT EXISTS (SELECT p.id FROM PartidaRecepcionFarmacia p WHERE p.movimiento = m) "
            + "ORDER BY m.fecha")
    List<Movimiento> despachosPorRecibir();
}
