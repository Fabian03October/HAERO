package com.example.backend.farmacia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "farmacia_devolucion_partida")
@Getter
@Setter
public class PartidaDevolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "devolucion_id", nullable = false)
    private Devolucion devolucion;

    @ManyToOne
    @JoinColumn(name = "entrega_partida_id", nullable = false)
    private PartidaEntrega partidaEntrega;

    @Column(nullable = false)
    private Integer piezas;

    // Lo dañado no regresa al inventario: se registra como merma (HU35).
    @Column(nullable = false)
    private boolean danado;
}
