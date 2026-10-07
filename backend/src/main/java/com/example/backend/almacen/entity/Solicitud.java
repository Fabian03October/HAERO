package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitud")
@Getter
@Setter
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    @Column(name = "cantidad_atendida", nullable = false)
    private Integer cantidadAtendida = 0;

    @Column(nullable = false, length = 20)
    private String estatus = "PENDIENTE";

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Bloqueo optimista: evita que dos despachadores atiendan la misma solicitud a la vez.
    @Version
    private Long version;
}
