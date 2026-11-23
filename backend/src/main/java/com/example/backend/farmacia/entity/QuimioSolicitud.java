package com.example.backend.farmacia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla quimio_solicitud. Hoy ServicioQuimioterapia, ServicioSincronizacionQuimio la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "quimio_solicitud")
@Getter
@Setter
public class QuimioSolicitud {

    @Id
    @Column(name = "folio", nullable = false)
    private Integer folio;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "fecha_requerida")
    private LocalDateTime fechaRequerida;

    @Column(name = "estatus", length = 20)
    private String estatus;

    @Column(name = "expediente", length = 30)
    private String expediente;

    @Nationalized
    @Column(name = "paciente", length = 250)
    private String paciente;

    @Nationalized
    @Column(name = "servicio_solicita", length = 100)
    private String servicioSolicita;

    @Nationalized
    @Column(name = "diagnostico", length = 1000)
    private String diagnostico;

    @Nationalized
    @Column(name = "notas", columnDefinition = "nvarchar(max)")
    private String notas;

    @Column(name = "medico_usuario", length = 20)
    private String medicoUsuario;

    @Nationalized
    @Column(name = "medico", length = 200)
    private String medico;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
