package com.example.backend.farmacia.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** HU31/HU32: día dado de baja por volumen en Intelisis. Un día sin corte está pendiente. */
@Entity
@Table(name = "farmacia_corte")
@Getter
@Setter
public class CorteFarmacia {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String DADO_DE_BAJA = "DADO_DE_BAJA";
    public static final String VERIFICADO = "VERIFICADO";

    @Id
    private LocalDate dia;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "folio_intelisis", length = 30)
    private String folioIntelisis;

    @Column(name = "marcado_en", nullable = false)
    private LocalDateTime marcadoEn;

    @ManyToOne
    @JoinColumn(name = "marcado_por", nullable = false)
    private Usuario marcadoPor;

    @Column(name = "verificado_en")
    private LocalDateTime verificadoEn;
}
