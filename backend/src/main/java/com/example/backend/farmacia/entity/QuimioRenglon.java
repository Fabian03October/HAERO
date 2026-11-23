package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;

/**
 * Tabla quimio_renglon. Hoy ServicioQuimioterapia la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quimio_renglon")
@Getter
@Setter
public class QuimioRenglon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preparacion_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuimioPreparacion preparacion;

    @Column(name = "texto_medico", length = 300)
    private String textoMedico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clave_indicada", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento claveIndicada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clave_alternativa", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento claveAlternativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clave_surtida", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento claveSurtida;

    @Column(name = "dosis", nullable = false, length = 100)
    private String dosis;

    @Column(name = "piezas", nullable = false)
    private Integer piezas;

    @Column(name = "dosis_cantidad", precision = 12, scale = 3)
    private BigDecimal dosisCantidad;

    @Column(name = "dosis_unidad", length = 10)
    private String dosisUnidad;

    @Column(name = "contenido_por_pieza", precision = 12, scale = 3)
    private BigDecimal contenidoPorPieza;

    @Column(name = "destino_sobrante", length = 10)
    private String destinoSobrante;

    @Column(name = "motivo_alternativa", length = 300)
    private String motivoAlternativa;

    @Column(name = "no_surtido", nullable = false)
    private Boolean noSurtido;

    @Nationalized
    @Column(name = "motivo_no_surtido", length = 200)
    private String motivoNoSurtido;
}
