package com.example.backend.farmacia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "farmacia_entrega_partida")
@Getter
@Setter
public class PartidaEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    @ManyToOne
    @JoinColumn(name = "receta_clave_id", nullable = false)
    private ClaveReceta claveReceta;

    @ManyToOne
    @JoinColumn(name = "farmacia_lote_id", nullable = false)
    private LoteFarmacia lote;

    @Column(nullable = false)
    private Integer piezas;

    @Column(name = "piezas_devueltas", nullable = false)
    private Integer piezasDevueltas = 0;
}
