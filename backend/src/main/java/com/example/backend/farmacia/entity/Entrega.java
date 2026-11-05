package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cada entrega de una receta: la primera o las que la completan (HU28, HU29). */
@Entity
@Table(name = "farmacia_entrega")
@Getter
@Setter
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Baja por volumen del área (Hospitalización, Quimioterapia, Quirófano) en la que ya va. Solo
    // lectura: la asigna "terminar" con SQL.
    @Column(name = "baja_volumen_folio", insertable = false, updatable = false)
    private Integer bajaVolumenFolio;

    @OneToMany(mappedBy = "entrega", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<PartidaEntrega> partidas = new ArrayList<>();

    /** Folio que ve el despachador: E-000123. */
    public String folio() {
        return folio(id);
    }

    public static String folio(Long id) {
        return id == null ? null : String.format("E-%06d", id);
    }
}
