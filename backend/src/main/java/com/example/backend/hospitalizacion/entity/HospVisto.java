package com.example.backend.hospitalizacion.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Tabla hosp_visto. Hoy ServicioHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_visto")
@IdClass(HospVisto.Llave.class)
@Getter
@Setter
public class HospVisto {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;

    @Id
    @Column(name = "espacio", nullable = false, length = 10)
    private String espacio;

    @Column(name = "visto_hasta", nullable = false)
    private LocalDateTime vistoHasta;

    /** Llave primaria compuesta (usuario_id, espacio). */
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Llave implements Serializable {
        private Long usuarioId;
        private String espacio;
    }
}
