package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Tabla conteo_inicial. Hoy ServicioConteoInicial la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "conteo_inicial")
@Getter
@Setter
public class ConteoInicial {

    @Id
    @Column(name = "area", nullable = false, length = 10)
    private String area;

    @Column(name = "cerrado_en", nullable = false)
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cerrado_por", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario cerradoPor;
}
