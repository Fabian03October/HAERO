package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.SolicitudPedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudPedidoRepository extends JpaRepository<SolicitudPedido, Long> {
}
