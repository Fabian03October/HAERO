package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.PedidoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {
    List<PedidoDetalle> findByPedidoId(Long pedidoId);
    Optional<PedidoDetalle> findByPedidoIdAndMedicamentoClave(Long pedidoId, String medicamentoClave);
}
