package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tabla farmacia_hoja_baja. Hoy ServicioBajaArea, ServicioHojasCarrito la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "farmacia_hoja_baja")
@Getter
@Setter
public class FarmaciaHojaBaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folio", nullable = false)
    private Integer folio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Receta receta;

    @Column(name = "dia", nullable = false)
    private LocalDate dia;

    @Column(name = "central", nullable = false, length = 10)
    private String central;

    @Column(name = "espacio", length = 10)
    private String espacio;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;
}
