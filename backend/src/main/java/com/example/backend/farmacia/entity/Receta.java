package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** HU28/HU29: receta que trae el paciente, con lo indicado y lo entregado de cada clave. */
@Entity
@Table(name = "farmacia_receta")
@Getter
@Setter
public class Receta {

    public static final String SURTIDA = "SURTIDA";
    public static final String PARCIAL = "PARCIAL";
    public static final String NO_DISPENSADA = "NO_DISPENSADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String folio;

    @Column(name = "paciente_nombre", nullable = false, length = 250)
    private String pacienteNombre;

    @Column(name = "paciente_expediente", length = 30)
    private String pacienteExpediente;

    @Column(name = "paciente_curp", length = 18)
    private String pacienteCurp;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ClaveReceta> claves = new ArrayList<>();
}
