package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lo que hizo el administrador con cada cuenta (HU41): alta, cambios, desactivación,
 * reactivación y restablecimiento de contraseña. Nunca guarda contraseñas.
 */
@Entity
@Table(name = "bitacora_usuario")
@Getter
@Setter
public class BitacoraUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    // Nombre de usuario de quien hizo el cambio.
    @Column(name = "realizado_por", nullable = false, length = 50)
    private String realizadoPor;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 30)
    private String accion;

    @Column(length = 400)
    private String detalle;
}
