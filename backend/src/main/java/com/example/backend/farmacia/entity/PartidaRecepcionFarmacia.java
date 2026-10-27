package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Movimiento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Una salida de Almacén (movimiento) recibida en Farmacia; solo se recibe una vez. */
@Entity
@Table(name = "farmacia_recepcion_partida")
@Getter
@Setter
public class PartidaRecepcionFarmacia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recepcion_id", nullable = false)
    private RecepcionFarmacia recepcion;

    @ManyToOne
    @JoinColumn(name = "movimiento_id", nullable = false)
    private Movimiento movimiento;

    @ManyToOne
    @JoinColumn(name = "farmacia_lote_id", nullable = false)
    private LoteFarmacia lote;

    @Column(name = "cajas_despachadas", nullable = false)
    private Integer cajasDespachadas;

    @Column(name = "cajas_recibidas", nullable = false)
    private Integer cajasRecibidas;

    @Column(name = "piezas_recibidas", nullable = false)
    private Integer piezasRecibidas;
}
