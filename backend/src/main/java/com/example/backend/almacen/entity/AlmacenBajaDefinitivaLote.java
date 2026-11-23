package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

/**
 * Tabla almacen_baja_definitiva_lote. Hoy ServicioBajaDefinitiva la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "almacen_baja_definitiva_lote")
@IdClass(AlmacenBajaDefinitivaLote.Llave.class)
@Getter
@Setter
public class AlmacenBajaDefinitivaLote {

    @Id
    @Column(name = "baja_id", nullable = false)
    private Long bajaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baja_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AlmacenBajaDefinitiva baja;

    @Id
    @Column(name = "lote_id", nullable = false)
    private Long loteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Lote lote;

    @Column(name = "cajas", nullable = false)
    private Integer cajas;

    /** Llave primaria compuesta (baja_id, lote_id). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long bajaId;
        private Long loteId;
    }
}
