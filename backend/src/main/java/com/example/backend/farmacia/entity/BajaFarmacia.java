package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** HU35: baja por caducidad, daño o extravío. No es consumo de pacientes. */
@Entity
@Table(name = "farmacia_baja")
@Getter
@Setter
public class BajaFarmacia {

    public static final String CADUCIDAD = "CADUCIDAD";
    public static final String DANO = "DANO";
    public static final String EXTRAVIO = "EXTRAVIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "farmacia_lote_id", nullable = false)
    private LoteFarmacia lote;

    @Column(nullable = false)
    private Integer piezas;

    @Column(nullable = false, length = 12)
    private String motivo;

    @Column(length = 300)
    private String detalle;

    // Si viene de lo dañado de una devolución (HU34), esa partida.
    @ManyToOne
    @JoinColumn(name = "devolucion_partida_id")
    private PartidaDevolucion partidaDevolucion;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
