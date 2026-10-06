package com.example.backend.almacen.entity;

import com.example.backend.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * PDF escaneado de un expediente de préstamo o transferencia. Tipos: SOLICITUD y
 * CIERRE (préstamo), ENVIO y RECEPCION (transferencia). El contenido solo se lee al
 * descargarlo; las listas usan DocumentoExternoRepository.resumenes, sin el archivo.
 */
@Entity
@Table(name = "documento_externo")
@Getter
@Setter
public class DocumentoExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "expediente_id", nullable = false)
    private ExpedienteExterno expediente;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(nullable = false)
    private Integer tamano;

    @Column(nullable = false, columnDefinition = "VARBINARY(MAX)")
    private byte[] contenido;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
