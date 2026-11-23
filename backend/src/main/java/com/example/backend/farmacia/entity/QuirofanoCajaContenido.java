package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.io.Serializable;

/**
 * Tabla quirofano_caja_contenido. Hoy ServicioCajasQuirofano la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_caja_contenido")
@IdClass(QuirofanoCajaContenido.Llave.class)
@Getter
@Setter
public class QuirofanoCajaContenido {

    @Id
    @Column(name = "caja_id", nullable = false)
    private Long cajaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoCaja caja;

    @Id
    @Column(name = "medicamento_clave", nullable = false, length = 30)
    private String medicamentoClave;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicamento_clave", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento medicamento;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "aparte", nullable = false)
    private Boolean aparte;

    @Nationalized
    @Column(name = "descripcion", length = 200)
    private String descripcion;

    /** Llave primaria compuesta (caja_id, medicamento_clave). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long cajaId;
        private String medicamentoClave;
    }
}
