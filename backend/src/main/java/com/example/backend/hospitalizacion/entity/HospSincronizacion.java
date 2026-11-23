package com.example.backend.hospitalizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla hosp_sincronizacion. Hoy ServicioHospitalizacion, ServicioSincronizacionHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_sincronizacion")
@Getter
@Setter
public class HospSincronizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    @Column(name = "fin")
    private LocalDateTime fin;

    @Column(name = "estatus", nullable = false, length = 10)
    private String estatus;

    @Column(name = "camas")
    private Integer camas;

    @Column(name = "estancias")
    private Integer estancias;

    @Column(name = "medicamentos")
    private Integer medicamentos;

    @Column(name = "eventos")
    private Integer eventos;

    @Nationalized
    @Column(name = "error", length = 1000)
    private String error;
}
