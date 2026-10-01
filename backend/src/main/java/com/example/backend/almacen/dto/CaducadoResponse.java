package com.example.backend.almacen.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class CaducadoResponse {
    private Long loteId;
    private String clave;
    private String nombreGenerico;
    private String numeroLote;
    private LocalDate caducidad;
    private String proveedor;
    private Integer cajas;
    private List<UbicacionCantidad> ubicaciones;
    // Solo en el apartado: cuando se movio y quien lo confirmo.
    private LocalDateTime fechaApartado;
    private String usuario;
}
