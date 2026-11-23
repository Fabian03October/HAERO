package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HU33: recorrido de un lote del pedido al paciente, o los lotes que recibió un paciente. */
@Getter
@AllArgsConstructor
public class TrazabilidadResponse {
    private String modo;
    private String valor;
    private List<PasoRecorridoResponse> recorrido;
    private List<EntregaPacienteResponse> entregas;
    // Aviso para la pantalla (por ejemplo, Intelisis no respondió); null si todo bien.
    private String aviso;
    // Solo por lote: compras de Intelisis con las que llegó y existencia de hoy en Intelisis.
    private List<Compra> compras;
    private List<Existencia> existencias;

    public TrazabilidadResponse(String modo, String valor, List<PasoRecorridoResponse> recorrido,
                                List<EntregaPacienteResponse> entregas, String aviso) {
        this(modo, valor, recorrido, entregas, aviso, List.of(), List.of());
    }

    /** Compra de Intelisis: origen = pedido u orden de la que viene; cantidad en unidades de Intelisis. */
    public record Compra(LocalDate fecha, String compra, String origen, Integer contrato, String proveedor,
                         String clave, BigDecimal cantidad, LocalDate caducidad, String almacen) {
    }

    /** Existencia de hoy en Intelisis: tipo = "Almacén" (clave base) o "Farmacia" (clave con "-1"). */
    public record Existencia(String almacen, String tipo, String clave, BigDecimal cantidad) {
    }
}
