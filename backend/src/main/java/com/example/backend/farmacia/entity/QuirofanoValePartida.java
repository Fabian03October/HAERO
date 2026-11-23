package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

/**
 * Tabla quirofano_vale_partida. Hoy ServicioCajasQuirofano la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_vale_partida")
@IdClass(QuirofanoValePartida.Llave.class)
@Getter
@Setter
public class QuirofanoValePartida {

    @Id
    @Column(name = "vale_id", nullable = false)
    private Long valeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vale_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoVale vale;

    @Id
    @Column(name = "medicamento_clave", nullable = false, length = 30)
    private String medicamentoClave;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicamento_clave", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento medicamento;

    @Column(name = "surtido", nullable = false)
    private Integer surtido;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    /** Llave primaria compuesta (vale_id, medicamento_clave). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long valeId;
        private String medicamentoClave;
    }
}
