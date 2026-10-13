package com.example.backend.almacen.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicamento")
@Getter
@Setter
public class Medicamento {

    @Id
    @Column(length = 30)
    private String clave;

    @Column(name = "nombre_generico", nullable = false, length = 1000)
    private String nombreGenerico;

    @Column(nullable = false, length = 1000)
    private String presentacion;

    @Column(name = "piezas_por_caja")
    private Integer piezasPorCaja;

    @Column(length = 200)
    private String descripcion;

    // Columna "CONSUMO PROM." de la hoja de carga inicial. Respaldo del CPM
    // (HU16) mientras la clave no tenga salidas propias registradas en el sistema.
    @Column(name = "consumo_promedio_hoja")
    private Double consumoPromedioHoja;

    // Datos que vienen de Intelisis (Art y ArtMedicamento). Las claves capturadas
    // a mano en HAERO los dejan vacíos.
    @Column(name = "forma_farmaceutica", length = 200)
    private String formaFarmaceutica;

    @Column(length = 2000)
    private String concentracion;

    @Column(name = "grupo_terapeutico", length = 100)
    private String grupoTerapeutico;

    @Column(name = "cuadro_basico")
    private Boolean cuadroBasico;

    // ALTA o BAJA, como lo maneja Intelisis.
    @Column(length = 15)
    private String estatus;

    @Column(name = "clave_cucop", length = 20)
    private String claveCucop;

    @Column(name = "unidad_compra", length = 50)
    private String unidadCompra;

    @Column(name = "unidad_minima", length = 50)
    private String unidadMinima;

    @Column(length = 30)
    private String riesgo;

    @Column(name = "principal_indicacion", length = 1000)
    private String principalIndicacion;

    @Column(length = 1000)
    private String contraindicaciones;

    @Column(name = "descripcion_completa", length = 255)
    private String descripcionCompleta;

    // HAERO = capturada aquí; INTELISIS = la trae la sincronización.
    @Column(nullable = false, length = 10)
    private String origen = "HAERO";

    @Column(name = "sincronizado_en")
    private LocalDateTime sincronizadoEn;
}
