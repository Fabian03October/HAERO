package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** HU34: medicamento que regresa un paciente o servicio, ligado a su entrega. */
@Entity
@Table(name = "farmacia_devolucion")
@Getter
@Setter
public class Devolucion {

    // Se resta en el resumen del día de la entrega (aún no dado de baja en Intelisis).
    public static final String RESTA = "RESTA";
    // El día de la entrega ya se dio de baja: aparece como "Devolucion Farmacia" en el de hoy.
    public static final String DEVOLUCION = "DEVOLUCION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "entrega_id", nullable = false)
    private Entrega entrega;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Column(name = "dia_resumen", nullable = false)
    private LocalDate diaResumen;

    @Column(name = "en_resumen", nullable = false, length = 12)
    private String enResumen;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL)
    private List<PartidaDevolucion> partidas = new ArrayList<>();
}
