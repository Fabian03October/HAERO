package com.example.backend.almacen.repository;

import org.springframework.data.jpa.repository.Query;

import com.example.backend.almacen.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    List<Movimiento> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);

    List<Movimiento> findByExistenciaLoteIdAndTipo(Long loteId, String tipo);

    List<Movimiento> findByTipoInOrderByFechaDesc(List<String> tipos);

    /** Lo que Almacén despachó a las claves de un pedido de Farmacia. */
    @org.springframework.data.jpa.repository.Query("SELECT m FROM Movimiento m WHERE m.tipo = 'SALIDA' AND m.solicitud.pedido.id = :pedido "
            + "ORDER BY m.solicitud.medicamento.clave, m.fecha")
    List<Movimiento> salidasDelPedido(@org.springframework.data.repository.query.Param("pedido") Long pedidoId);

    Optional<Movimiento> findFirstByTipoOrderByFechaAsc(String tipo);

    List<Movimiento> findByExpedienteIdInOrderByFechaAsc(Collection<Long> expedientes);

    /** Cajas apartadas en caducados de un lote (suma de sus movimientos BAJA_CADUCIDAD). */
    @Query("SELECT COALESCE(SUM(m.cantidadCajas), 0) FROM Movimiento m WHERE m.existencia.lote.id = :loteId AND m.tipo = 'BAJA_CADUCIDAD'")
    int cajasEnCaducados(Long loteId);
}
