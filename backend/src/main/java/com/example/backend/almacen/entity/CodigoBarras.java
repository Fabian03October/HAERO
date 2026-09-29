package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "codigo_barras")
@Getter
@Setter
public class CodigoBarras {

    @Id
    @Column(length = 60)
    private String codigo;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(nullable = false, length = 120)
    private String proveedor;
}
