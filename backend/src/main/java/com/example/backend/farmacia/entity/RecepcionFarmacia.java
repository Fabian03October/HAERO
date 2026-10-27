package com.example.backend.farmacia.entity;

import com.example.backend.almacen.entity.Solicitud;
import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** HU26: lo que Farmacia confirmó que llegó de un despacho de Almacén. */
@Entity
@Table(name = "farmacia_recepcion")
@Getter
@Setter
public class RecepcionFarmacia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @Column(name = "motivo_diferencia", length = 300)
    private String motivoDiferencia;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL)
    private List<PartidaRecepcionFarmacia> partidas = new ArrayList<>();
}
