package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "medicamento")
@Getter
@Setter
public class Medicamento {

    @Id
    @Column(length = 30)
    private String clave;

    @Column(name = "nombre_generico", nullable = false, length = 150)
    private String nombreGenerico;

    @Column(nullable = false, length = 80)
    private String presentacion;

    @Column(name = "piezas_por_caja")
    private Integer piezasPorCaja;

    @Column(length = 200)
    private String descripcion;
}
