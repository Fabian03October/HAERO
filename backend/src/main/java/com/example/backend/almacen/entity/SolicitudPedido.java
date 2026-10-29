package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** HU39: encabezado de un pedido de Farmacia; cada clave es una Solicitud ligada a él. */
@Entity
@Table(name = "solicitud_pedido")
@Getter
@Setter
public class SolicitudPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(length = 300)
    private String observaciones;
}
