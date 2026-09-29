package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento")
@Getter
@Setter
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "existencia_id", nullable = false)
    private Existencia existencia;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "cantidad_cajas", nullable = false)
    private Integer cantidadCajas;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;
}
