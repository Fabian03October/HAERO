package com.example.backend.farmacia.dto;

/**
 * Lo entregado en el día por un área que tiene su propia baja por volumen (Hospitalización,
 * Quimioterapia, Quirófano). Va aparte del resumen de ventanilla: se captura en Intelisis con la
 * hoja de baja de esa área, NO con la baja del día, para no darlo de baja dos veces.
 */
public record ResumenAreaResponse(String area, String nombre, int piezas, int claves, int pacientes,
                                  int entregasSinBaja, String foliosBaja) {
}
