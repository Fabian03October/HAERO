package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla quirofano_vale_uso. Hoy ServicioCajasQuirofano la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_vale_uso")
@Getter
@Setter
public class QuirofanoValeUso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vale_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoVale vale;

    @Column(name = "paciente_expediente", nullable = false, length = 30)
    private String pacienteExpediente;

    @Column(name = "paciente_nombre", nullable = false, length = 250)
    private String pacienteNombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_clave", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento medicamento;

    @Column(name = "piezas", nullable = false)
    private Integer piezas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrega_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Entrega entrega;
}
