package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Tabla quimio_reprogramacion: el paciente no pudo pasar y Oncología dio otra fecha (la última manda). */
@Entity
@Table(name = "quimio_reprogramacion")
@Getter
@Setter
public class QuimioReprogramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "folio", nullable = false)
    private Integer folio;

    @Column(name = "fecha_anterior")
    private LocalDateTime fechaAnterior;

    @Column(name = "fecha_nueva", nullable = false)
    private LocalDate fechaNueva;

    @Nationalized
    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
