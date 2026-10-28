package com.example.backend.farmacia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Un medicamento indicado del paciente (agrupado por clave) en el carrito del día. */
@Getter
@AllArgsConstructor
public class LineaCarrito {
    private String clave;
    private String medicamento;
    // Clave del catálogo de HAERO; si no, solo se muestra (no se puede surtir por escaneo).
    private boolean enCatalogo;
    // Referencia para el despachador (la cantidad la captura él): "1 GRAMO INTRAVENOSA c/8 horas".
    private String posologia;
    private Integer dosis24h;
    private int entregadas;
    // PENDIENTE, SURTIDO o SUSPENDIDO (se canceló después de meterlo al carrito).
    private String estado;
    private LocalDateTime indicadoEn;
    // Lotes entregados hoy: "A123 (3), B456 (1)".
    private String lotes;
    // Se indicó hoy después de la hora de corte (1:00 p.m.): se surte si urge; si no, mañana.
    private boolean despuesDelCierre;
    // Se indicó ayer y no se surtió.
    private boolean pendienteDeAyer;
    // Hoy le cambiaron la dosis, la vía o la frecuencia (solo aviso).
    private boolean modificado;
    // Cuándo y qué médico lo canceló, si se canceló.
    private LocalDateTime canceladoEn;
    private String canceladoPor;
}
