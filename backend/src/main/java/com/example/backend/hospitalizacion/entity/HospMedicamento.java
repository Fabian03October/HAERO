package com.example.backend.hospitalizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla hosp_medicamento. Hoy ServicioCarrito, ServicioHojasCarrito, ServicioHospitalizacion, ServicioSincronizacionHospitalizacion la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "hosp_medicamento")
@Getter
@Setter
public class HospMedicamento {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "id_ind_medica", nullable = false)
    private Integer idIndMedica;

    @Column(name = "id_ingreso", nullable = false)
    private Integer idIngreso;

    @Column(name = "clave", length = 40)
    private String clave;

    @Nationalized
    @Column(name = "medicamento", length = 255)
    private String medicamento;

    @Column(name = "via", length = 20)
    private String via;

    @Column(name = "dosis")
    private Float dosis;

    @Column(name = "unidad", length = 10)
    private String unidad;

    @Column(name = "cada")
    private Integer cada;

    @Column(name = "cada_unidad", length = 10)
    private String cadaUnidad;

    @Column(name = "indicado_en")
    private LocalDateTime indicadoEn;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "suspendido_en")
    private LocalDateTime suspendidoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Nationalized
    @Column(name = "nota", length = 1000)
    private String nota;

    @Column(name = "folio")
    private Integer folio;

    @Column(name = "externo")
    private Boolean externo;

    @Column(name = "dosis_24h")
    private Integer dosis24h;

    @Column(name = "unidosis")
    private Boolean unidosis;

    @Column(name = "inicia")
    private LocalDateTime inicia;

    @Column(name = "infusion")
    private Boolean infusion;

    @Column(name = "cantidad_hora")
    private Float cantidadHora;

    @Column(name = "numero_horas")
    private Integer numeroHoras;

    @Column(name = "indico", length = 20)
    private String indico;

    @Column(name = "suspendio", length = 20)
    private String suspendio;
}
