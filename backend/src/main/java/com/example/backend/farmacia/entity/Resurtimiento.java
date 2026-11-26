package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Tabla farmacia_resurtimiento: una receta de Ventanilla ya surtida que se volvió a abrir con las mismas cantidades. */
@Entity
@Table(name = "farmacia_resurtimiento")
@Getter
@Setter
public class Resurtimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Receta receta;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Nationalized
    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
