package com.example.backend.almacen.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cajas que Almacén no surtió de una solicitud en un despacho, con su motivo, quién y cuándo. */
@Entity
@Table(name = "solicitud_faltante")
@Getter
@Setter
public class SolicitudFaltante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @Column(nullable = false)
    private Integer cajas;

    @Column(nullable = false, length = 300)
    private String motivo;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}
