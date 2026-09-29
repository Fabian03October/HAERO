package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "existencia")
@Getter
@Setter
public class Existencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(nullable = false, length = 40)
    private String ubicacion;

    @Column(name = "cantidad_cajas", nullable = false)
    private Integer cantidadCajas = 0;
}
