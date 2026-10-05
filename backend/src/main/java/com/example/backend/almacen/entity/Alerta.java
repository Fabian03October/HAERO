package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerta")
@Getter
@Setter
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    // Solo para CADUCIDAD_PROXIMA: el lote especifico que esta por vencer.
    // DESABASTO/SOBREABASTO son por clave, no por lote, y lo dejan en null.
    @ManyToOne
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    // Fotografia del momento en que se abrio la alerta, para el historial (HU17 CA5).
    @Column(name = "existencia_al_inicio", nullable = false)
    private int existenciaAlInicio;

    @Column(name = "limite_al_inicio", nullable = false)
    private double limiteAlInicio;
}
