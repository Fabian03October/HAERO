package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bitácora de piezas de Farmacia: positivo entra, negativo sale. */
@Entity
@Table(name = "farmacia_movimiento")
@Getter
@Setter
public class MovimientoFarmacia {

    public static final String ALTA_INICIAL = "ALTA_INICIAL";
    public static final String RECEPCION = "RECEPCION";
    public static final String ENTREGA = "ENTREGA";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String BAJA = "BAJA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "farmacia_lote_id", nullable = false)
    private LoteFarmacia lote;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private Integer piezas;

    // Renglón de la tabla del tipo (partida de recepción, de entrega, etc.) que lo originó.
    @Column(name = "referencia_id")
    private Long referenciaId;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
