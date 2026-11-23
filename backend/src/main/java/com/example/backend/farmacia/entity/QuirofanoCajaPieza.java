package com.example.backend.farmacia.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

/**
 * Tabla quirofano_caja_pieza. Hoy ServicioCajasQuirofano, ServicioIntelisis la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_caja_pieza")
@IdClass(QuirofanoCajaPieza.Llave.class)
@Getter
@Setter
public class QuirofanoCajaPieza {

    @Id
    @Column(name = "caja_id", nullable = false)
    private Long cajaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoCaja caja;

    @Id
    @Column(name = "farmacia_lote_id", nullable = false)
    private Long farmaciaLoteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmacia_lote_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private LoteFarmacia farmaciaLote;

    @Column(name = "piezas", nullable = false)
    private Integer piezas;

    /** Llave primaria compuesta (caja_id, farmacia_lote_id). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long cajaId;
        private Long farmaciaLoteId;
    }
}
