package com.example.backend.farmacia.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Peticiones y respuestas de las cajas de anestesia de quirófano. */
public final class CajasQuirofanoDtos {

    private CajasQuirofanoDtos() {
    }

    // ---------- Peticiones ----------

    /**
     * aparte = está en el vale pero no se guarda en la caja (red fría): se entrega al momento.
     * descripcion = el nombre como lo escribe Farmacia en la caja y el vale; vacío = el del catálogo.
     */
    public record ContenidoRequest(String clave, Integer cantidad, Boolean aparte, String descripcion) {
    }

    /** Alta o cambio de una caja. El código es opcional al darla de alta (si no, HAERO lo genera). */
    public record GuardarCajaRequest(String codigo, String nombre, String servicio, List<ContenidoRequest> contenido,
                                     String justificacion) {
    }

    /** Meter piezas a la caja. Sin lote se toman en orden FEFO; "codigo" es el código de barras escaneado. */
    public record ArmarRequest(String clave, String lote, Integer piezas, String codigo) {
    }

    /** Regresar piezas de la caja al estante (por ejemplo, un lote por caducar). */
    public record SacarRequest(String clave, String lote, Integer piezas, String motivo) {
    }

    /** Dar de baja o reactivar una caja: siempre con motivo (queda en su historial). */
    public record MotivoCajaRequest(String motivo) {
    }

    /** Caja dada de baja: ya no se arma ni se entrega, pero su historial se conserva. */
    public record CajaDadaDeBaja(String codigo, String nombre, String servicio, LocalDateTime fecha, String usuario, String motivo) {
    }

    /** medicoUsuario: el usuario de Intelisis del médico, si se eligió de la lista (si no, solo el nombre). */
    public record EntregarRequest(String turno, String sala, String medico, String medicoUsuario) {
    }

    public record UsoRequest(String expediente, String nombre, String clave, Integer piezas) {
    }

    /** indicaciones: el expediente de cada PDF de la parte "documentos", en el mismo orden (uno por paciente). */
    public record RecibirRequest(List<UsoRequest> usos, String justificacion, List<String> indicaciones) {
    }

    public record MedicoResponse(String usuario, String nombre, String especialidad, String cedula) {
    }

    // ---------- Respuestas ----------

    public record LoteEnCaja(String lote, LocalDate caducidad, int piezas) {
    }

    /** aparte = red fría: no se guarda en la caja (no cuenta como faltante); sale al entregar. */
    /** medicamento = el nombre que se muestra (el propio si Farmacia lo reescribió); descripcion = el propio o null. */
    public record RenglonCaja(String clave, String medicamento, int cantidad, int tiene, int falta, List<LoteEnCaja> lotes,
                              boolean aparte, String descripcion, String nombreCatalogo) {
    }

    public record UsoResponse(String expediente, String nombre, String clave, int piezas, String folioEntrega) {
    }

    /** Renglón del vale impreso: Surtido (al entregar), Utilizado (con pacientes) y Devolución = surtido - utilizado. */
    public record PartidaVale(String clave, String medicamento, int surtido, int utilizado) {
    }

    public record ValeResponse(Long id, String turno, String sala, String medico, LocalDateTime entregadoEn, String entregadoPor,
                               LocalDateTime recibidoEn, String recibidoPor, String justificacion, List<String> indicaciones,
                               List<UsoResponse> usos, List<PartidaVale> partidas) {
    }

    public record EventoCaja(String accion, String detalle, LocalDateTime fecha, String usuario) {
    }

    /** estado: INCOMPLETA, COMPLETA o ENTREGADA. */
    public record CajaResponse(String codigo, String nombre, String servicio, String estado, int piezasFaltantes,
                               List<RenglonCaja> renglones, ValeResponse valeAbierto, List<ValeResponse> vales,
                               List<EventoCaja> historial) {
    }

    public record CodigoResponse(String clave, String medicamento) {
    }
}
