package com.example.backend.hospitalizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla hosp_evento. Hoy ServicioCarrito, ServicioHospitalizacion, ServicioSincronizacionHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_evento")
@Getter
@Setter
public class HospEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "tipo", nullable = false, length = 25)
    private String tipo;

    @Column(name = "id_ingreso", nullable = false)
    private Integer idIngreso;

    @Column(name = "espacio", length = 10)
    private String espacio;

    @Column(name = "central", length = 10)
    private String central;

    @Nationalized
    @Column(name = "detalle", nullable = false, length = 400)
    private String detalle;

    @Column(name = "medico", length = 20)
    private String medico;

    @Column(name = "clave", length = 40)
    private String clave;
}
