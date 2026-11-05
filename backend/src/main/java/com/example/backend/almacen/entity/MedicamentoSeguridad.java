package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Marcas de seguridad del paciente de una clave: alto riesgo (lista ISMP) y red fría.
 * Tabla aparte de medicamento porque la sincronización con Intelisis la reescribe.
 */
@Entity
@Table(name = "medicamento_seguridad")
@Getter
@Setter
public class MedicamentoSeguridad {

    @Id
    @Column(length = 30)
    private String clave;

    // null = se usa lo que dice Intelisis (riesgo = ALTO); true o false = lo decidió Farmacia.
    @Column(name = "alto_riesgo")
    private Boolean altoRiesgo;

    @Column(name = "red_fria", nullable = false)
    private boolean redFria;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
