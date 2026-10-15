package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lote")
@Getter
@Setter
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(name = "numero_lote", nullable = false, length = 40)
    private String numeroLote;

    @Column(nullable = false)
    private LocalDate caducidad;

    @Column(nullable = false, length = 120)
    private String proveedor;

    @Column(nullable = false, length = 20)
    private String estatus = "DISPONIBLE";

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    // Lote al que sustituye cuando entró por un canje (HU18 CA3).
    @ManyToOne
    @JoinColumn(name = "lote_origen_id")
    private Lote loteOrigen;

    // "Foto" del registro de Intelisis del que salió el lote (V19). Se guarda al
    // recibir y no cambia aunque Intelisis cambie después.
    // COMPRA, INV o EXISTENCIA (alta inicial).
    @Column(name = "origen_intelisis", length = 12)
    private String origenIntelisis;

    @Column(name = "id_intelisis")
    private Integer idIntelisis;

    @Column(name = "movimiento_intelisis", length = 60)
    private String movimientoIntelisis;

    @Column(name = "referencia_intelisis", length = 100)
    private String referenciaIntelisis;

    @Column(name = "lote_intelisis", length = 50)
    private String loteIntelisis;

    @Column(name = "fecha_intelisis")
    private LocalDate fechaIntelisis;

    @Column(name = "piezas_intelisis", precision = 19, scale = 4)
    private BigDecimal piezasIntelisis;

    @Column(name = "cajas_intelisis", precision = 19, scale = 4)
    private BigDecimal cajasIntelisis;

    // Cajas que Almacén contó al recibir.
    @Column(name = "cajas_confirmadas")
    private Integer cajasConfirmadas;

    @Column(length = 300)
    private String observaciones;

    @Column(length = 60)
    private String contrato;

    @Column(name = "fecha_alta", insertable = false, updatable = false)
    private LocalDateTime fechaAlta;
}
