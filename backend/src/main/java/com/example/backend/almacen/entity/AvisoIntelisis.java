package com.example.backend.almacen.entity;

import com.example.backend.usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

/**
 * Tabla aviso_intelisis. Hoy ServicioIntelisis, ServicioSincronizacionIntelisis la lee y escribe con SQL directo
 * (JdbcTemplate); esta clase documenta sus columnas y relaciones para usarla con JPA.
 * Generada de la estructura real de la base (Flyway); las llaves foráneas viven en las migraciones.
 */
@Entity
@Table(name = "aviso_intelisis")
@Getter
@Setter
public class AvisoIntelisis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "tipo", nullable = false, length = 25)
    private String tipo;

    @Column(name = "nivel", nullable = false, length = 10)
    private String nivel;

    @Nationalized
    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Nationalized
    @Column(name = "detalle", length = 600)
    private String detalle;

    @Column(name = "medicamento_clave", length = 30)
    private String medicamentoClave;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Lote lote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Pedido pedido;

    @Column(name = "revisado_en")
    private LocalDateTime revisadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Usuario revisadoPor;
}
