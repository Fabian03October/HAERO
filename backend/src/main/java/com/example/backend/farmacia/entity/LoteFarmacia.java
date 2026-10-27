package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Lote;
import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lote en el inventario de Farmacia (HU27), en piezas. */
@Entity
@Table(name = "farmacia_lote")
@Getter
@Setter
public class LoteFarmacia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(name = "numero_lote", nullable = false, length = 50)
    private String numeroLote;

    @Column(nullable = false)
    private LocalDate caducidad;

    // Lote de Almacén del que salió; vacío si solo se conoce por el alta inicial (HU25).
    @ManyToOne
    @JoinColumn(name = "lote_id")
    private Lote loteAlmacen;

    @Column(nullable = false)
    private Integer piezas = 0;

    @Column(name = "fecha_alta", insertable = false, updatable = false)
    private LocalDateTime fechaAlta;

    // Bloqueo optimista: dos entregas simultáneas no pueden descontar las mismas piezas.
    @Version
    private Long version;
}
