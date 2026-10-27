package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Medicamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "farmacia_receta_clave")
@Getter
@Setter
public class ClaveReceta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @ManyToOne
    @JoinColumn(name = "medicamento_clave", nullable = false)
    private Medicamento medicamento;

    @Column(name = "piezas_indicadas", nullable = false)
    private Integer piezasIndicadas;

    @Column(name = "piezas_entregadas", nullable = false)
    private Integer piezasEntregadas = 0;

    // HU29: la clave no se pudo surtir; no descuenta existencias.
    @Column(name = "no_dispensada", nullable = false)
    private boolean noDispensada;

    @Column(length = 300)
    private String motivo;

    public int pendiente() {
        return Math.max(0, piezasIndicadas - piezasEntregadas);
    }
}
