package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

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
}
