package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Tabla farmacia_consulta_trazabilidad. Hoy ServicioTrazabilidad la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "farmacia_consulta_trazabilidad")
@Getter
@Setter
public class FarmaciaConsultaTrazabilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;

    @Column(name = "modo", nullable = false, length = 12)
    private String modo;

    @Column(name = "valor", nullable = false, length = 60)
    private String valor;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
