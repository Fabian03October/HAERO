package com.example.backend.hospitalizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla hosp_medico. Hoy ServicioCajasQuirofano, ServicioCarrito, ServicioHospitalizacion, ServicioSincronizacionHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_medico")
@Getter
@Setter
public class HospMedico {

    @Id
    @Column(name = "usuario", nullable = false, length = 20)
    private String usuario;

    @Nationalized
    @Column(name = "nombre", length = 200)
    private String nombre;

    @Column(name = "cedula", length = 50)
    private String cedula;

    @Nationalized
    @Column(name = "especialidad", length = 100)
    private String especialidad;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
