package com.example.backend.almacen.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Entrada (HU10) o alta inicial (HU21): la caja escaneada, el registro de Intelisis que
 * eligió Almacén y dónde quedaron las cajas. Lote, caducidad y proveedor no se mandan:
 * salen del registro de Intelisis.
 */
@Getter
@Setter
public class RegistrarEntradaRequest {
    // Código de barras de la caja; si es nuevo, se asocia a la clave.
    private String codigo;
    private String clave;
    // COMPRA, INV o EXISTENCIA, e id de Intelisis (vacío en EXISTENCIA).
    private String origen;
    private Integer idIntelisis;
    private String lote;
    private List<UbicacionCantidad> ubicaciones;
    // Opcional: p. ej. por qué las cajas contadas no coinciden con Intelisis.
    private String observaciones;
}
