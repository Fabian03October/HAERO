package com.example.backend.hospitalizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla hosp_estancia. Hoy ServicioCarrito, ServicioHospitalizacion, ServicioSincronizacionHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_estancia")
@Getter
@Setter
public class HospEstancia {

    @Id
    @Column(name = "id_ing_area", nullable = false)
    private Integer idIngArea;

    @Column(name = "id_ingreso", nullable = false)
    private Integer idIngreso;

    @Column(name = "espacio", length = 10)
    private String espacio;

    @Nationalized
    @Column(name = "servicio", length = 100)
    private String servicio;

    @Column(name = "expediente", length = 20)
    private String expediente;

    @Nationalized
    @Column(name = "paciente", length = 250)
    private String paciente;

    @Column(name = "fecha_ingreso")
    private LocalDateTime fechaIngreso;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

    @Column(name = "egreso_detectado")
    private LocalDateTime egresoDetectado;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
