package com.example.backend.farmacia.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

/**
 * Tabla quirofano_vale_indicacion. Hoy ServicioCajasQuirofano la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_vale_indicacion")
@IdClass(QuirofanoValeIndicacion.Llave.class)
@Getter
@Setter
public class QuirofanoValeIndicacion {

    @Id
    @Column(name = "vale_id", nullable = false)
    private Long valeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vale_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoVale vale;

    @Id
    @Column(name = "paciente_expediente", nullable = false, length = 30)
    private String pacienteExpediente;

    @Column(name = "documento_nombre", nullable = false, length = 255)
    private String documentoNombre;

    @Column(name = "documento", nullable = false, columnDefinition = "varbinary(max)")
    private byte[] documento;

    /** Llave primaria compuesta (vale_id, paciente_expediente). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long valeId;
        private String pacienteExpediente;
    }
}
