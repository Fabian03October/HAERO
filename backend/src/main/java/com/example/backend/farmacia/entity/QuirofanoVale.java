package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Tabla quirofano_vale. Hoy ServicioCajasQuirofano la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quirofano_vale")
@Getter
@Setter
public class QuirofanoVale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private QuirofanoCaja caja;

    @Column(name = "turno", nullable = false, length = 15)
    private String turno;

    @Column(name = "sala", nullable = false, length = 100)
    private String sala;

    @Column(name = "medico", nullable = false, length = 150)
    private String medico;

    @Column(name = "entregado_en", nullable = false)
    private LocalDateTime entregadoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entregado_por", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario entregadoPor;

    @Column(name = "recibido_en")
    private LocalDateTime recibidoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recibido_por", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario recibidoPor;

    @Column(name = "justificacion", length = 500)
    private String justificacion;

    @Column(name = "indicacion_nombre", length = 255)
    private String indicacionNombre;

    @Column(name = "indicacion_pdf", columnDefinition = "varbinary(max)")
    private byte[] indicacionPdf;

    @Column(name = "medico_usuario", length = 20)
    private String medicoUsuario;
}
