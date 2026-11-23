package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tabla conteo_inicial_renglon. Hoy ServicioConteoInicial la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "conteo_inicial_renglon")
@Getter
@Setter
public class ConteoInicialRenglon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "area", nullable = false, length = 10)
    private String area;

    @Column(name = "codigo", nullable = false, length = 60)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_clave", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Medicamento medicamento;

    @Column(name = "lote", nullable = false, length = 50)
    private String lote;

    @Column(name = "caducidad", nullable = false)
    private LocalDate caducidad;

    @Column(name = "ubicacion", length = 40)
    private String ubicacion;

    @Column(name = "cantidad")
    private Integer cantidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contado_por", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario contadoPor;

    @Column(name = "contado_en")
    private LocalDateTime contadoEn;
}
