package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tabla farmacia_carrito_cierre. Hoy ServicioCarrito la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "farmacia_carrito_cierre")
@IdClass(FarmaciaCarritoCierre.Llave.class)
@Getter
@Setter
public class FarmaciaCarritoCierre {

    @Id
    @Column(name = "central", nullable = false, length = 10)
    private String central;

    @Id
    @Column(name = "dia", nullable = false)
    private LocalDate dia;

    @Column(name = "cerrado_en", nullable = false)
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;

    /** Llave primaria compuesta (central, dia). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private String central;
        private LocalDate dia;
    }
}
