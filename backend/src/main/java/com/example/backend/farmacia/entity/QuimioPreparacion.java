package com.example.backend.farmacia.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Tabla quimio_preparacion. Hoy ServicioQuimioterapia la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quimio_preparacion")
@Getter
@Setter
public class QuimioPreparacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "folio_solicitud", nullable = false, length = 30)
    private String folioSolicitud;

    @Column(name = "expediente_solicitud", nullable = false, length = 30)
    private String expedienteSolicitud;

    @Column(name = "nombre_solicitud", length = 250)
    private String nombreSolicitud;

    @Column(name = "paciente_expediente", nullable = false, length = 30)
    private String pacienteExpediente;

    @Column(name = "paciente_nombre", nullable = false, length = 250)
    private String pacienteNombre;

    @Column(name = "paciente_curp", length = 18)
    private String pacienteCurp;

    @Column(name = "expediente_corregido", nullable = false)
    private Boolean expedienteCorregido;

    @Column(name = "justificacion_paciente", length = 300)
    private String justificacionPaciente;

    @Column(name = "nota_medico", length = 2000)
    private String notaMedico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entrega_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Entrega entrega;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "surtio_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario surtio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifico_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario verifico;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "sesion", nullable = false)
    private Integer sesion;

    @Column(name = "sin_intelisis", nullable = false)
    private Boolean sinIntelisis;
}
