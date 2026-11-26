package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un lote de Farmacia cambió de estante: de dónde a dónde, quién y cuándo. */
@Entity
@Table(name = "farmacia_cambio_ubicacion")
@Getter
@Setter
public class CambioUbicacionFarmacia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "farmacia_lote_id", nullable = false)
    private LoteFarmacia lote;

    @Column(length = 40)
    private String anterior;

    @Column(nullable = false, length = 40)
    private String nueva;

    @Column(nullable = false, length = 20)
    private String origen;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
