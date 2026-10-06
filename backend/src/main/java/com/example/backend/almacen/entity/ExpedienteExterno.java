package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Expediente de un préstamo o una transferencia con otra institución (HU20).
 * Agrupa los movimientos de inventario (salida o entrada inicial y, en préstamos,
 * las devoluciones), los documentos PDF escaneados y el estatus.
 */
@Entity
@Table(name = "expediente_externo")
@Getter
@Setter
public class ExpedienteExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PRESTAMO o TRANSFERENCIA.
    @Column(nullable = false, length = 20)
    private String tipo;

    // SALIDA: el hospital presta o transfiere. ENTRADA: el hospital recibe.
    @Column(nullable = false, length = 10)
    private String sentido;

    @Column(nullable = false, length = 120)
    private String institucion;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(nullable = false)
    private Integer cajas;

    @Column(name = "cajas_devueltas", nullable = false)
    private Integer cajasDevueltas = 0;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // Solo préstamos: fecha comprometida de devolución.
    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    // PRESTAMO: ACTIVO, DEVUELTO_PARCIAL, CERRADO. TRANSFERENCIA: REGISTRADA, CONFIRMADA.
    @Column(nullable = false, length = 20)
    private String estatus;

    @Column(length = 500)
    private String observaciones;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
