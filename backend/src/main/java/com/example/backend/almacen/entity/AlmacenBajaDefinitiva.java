package com.example.backend.almacen.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tabla almacen_baja_definitiva. Hoy ServicioBajaDefinitiva la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "almacen_baja_definitiva")
@Getter
@Setter
public class AlmacenBajaDefinitiva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "folio_acta", nullable = false, length = 60)
    private String folioActa;

    @Column(name = "empresa", nullable = false, length = 150)
    private String empresa;

    @Column(name = "fecha_recoleccion", nullable = false)
    private LocalDate fechaRecoleccion;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "documento_nombre", nullable = false, length = 255)
    private String documentoNombre;

    @Column(name = "documento", nullable = false, columnDefinition = "varbinary(max)")
    private byte[] documento;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario usuario;
}
