package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pedido_detalle")
@Getter
@Setter
public class PedidoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(name = "cantidad_esperada", nullable = false)
    private Integer cantidadEsperada;

    // Lo que Almacén recibió físicamente en HAERO. La sincronización nunca lo toca.
    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida = 0;

    // Lo que dice Intelisis de esta partida (V18).
    @Column(length = 20)
    private String cucop;

    @Column(name = "recibida_intelisis")
    private Integer recibidaIntelisis;

    @Column(name = "cancelada_intelisis")
    private Integer canceladaIntelisis;
}
