package com.example.backend.farmacia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Peticiones y respuestas de quimioterapia. */
public final class QuimioterapiaDtos {

    private QuimioterapiaDtos() {
    }

    public record PacienteQuimio(String nombre, String expediente, String curp) {
    }

    /**
     * Un medicamento de la nota del médico ya pasado a clave. "alternativa" es la clave que el
     * médico indicó por si no hay la principal; "usarAlternativa" dice cuál se surtió (si la principal
     * sí tiene existencia, pide "motivoAlternativa"). Sin lote, Farmacia toma el que sale primero.
     * dosisCantidad/dosisUnidad (ej. 280 mg) y contenidoPorPieza (ej. 300 mg por frasco) son opcionales:
     * con ellos se calcula lo que sobra y "destinoSobrante" dice si se desecha (MERMA) o se guarda (RESGUARDO).
     */
    public record RenglonQuimioRequest(String textoMedico, String clave, String alternativa, boolean usarAlternativa,
                                       String motivoAlternativa, String dosis, Integer piezas, String lote,
                                       BigDecimal dosisCantidad, String dosisUnidad, BigDecimal contenidoPorPieza,
                                       String destinoSobrante) {
    }

    /** justificacionSinIntelisis: si Intelisis no responde, cómo se confirmó al paciente (expediente físico). */
    public record RegistrarQuimioRequest(String folio, String expedienteSolicitud, String nombreSolicitud,
                                         PacienteQuimio paciente, boolean expedienteCorregido, String justificacionPaciente,
                                         String notaMedico, List<RenglonQuimioRequest> renglones,
                                         String verificadorUsuario, String verificadorContrasena,
                                         String justificacionSinIntelisis) {
    }

    /** nombreIndicada y nombreAlternativa sirven para repetir lo de un ciclo anterior. */
    public record RenglonQuimioResponse(String textoMedico, String claveIndicada, String claveAlternativa, String claveSurtida,
                                        String medicamento, String dosis, int piezas, String motivoAlternativa,
                                        BigDecimal dosisCantidad, String dosisUnidad, BigDecimal contenidoPorPieza,
                                        BigDecimal sobrante, String destinoSobrante, String nombreIndicada, String nombreAlternativa) {
    }

    public record QuimioResponse(Long id, String folio, int sesion, String expedienteSolicitud, String pacienteNombre,
                                 String pacienteExpediente, boolean expedienteCorregido, String justificacionPaciente,
                                 boolean sinIntelisis, String notaMedico, String folioEntrega, LocalDateTime fecha,
                                 String surtio, String verifico, List<RenglonQuimioResponse> renglones,
                                 List<PartidaEntregadaResponse> partidas) {
    }

    /** Sesiones anteriores de la misma solicitud (para avisar y validar que sea el mismo paciente). */
    public record SesionesSolicitud(String folio, String pacienteExpediente, String pacienteNombre, List<QuimioResponse> sesiones) {
    }

    // ---------- Solicitudes de Oncología leídas de Intelisis ----------

    /** Una solicitud de la lista. folio es el "Folio" de la hoja impresa (ID_AuxTxSol en Intelisis). */
    public record SolicitudQuimio(int folio, LocalDateTime fecha, LocalDateTime fechaRequerida, String estatus, String expediente,
                                  String paciente, String servicioSolicita, String medico, int sesionesSurtidas,
                                  LocalDateTime ultimaSesion) {
    }

    /**
     * Clave del catálogo que puede ser la que escribió el médico. contenidoPorPieza y unidadContenido salen
     * de la concentración del catálogo (ej. 300 mg); existencia son las piezas vigentes en Farmacia.
     */
    public record ClaveSugerida(String clave, String nombre, String forma, String concentracion, BigDecimal contenidoPorPieza,
                                String unidadContenido, int existencia, boolean usadaAntes) {
    }

    /** Un medicamento que HAERO encontró en la nota, con la dosis escrita junto a él y sus claves posibles. */
    public record MedicamentoEnNota(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad,
                                    List<ClaveSugerida> claves) {
    }

    /** La solicitud completa, la nota pasada a sugerencias y lo que se surtió al paciente la vez anterior. */
    public record SolicitudQuimioDetalle(SolicitudQuimio solicitud, String diagnostico, String notas,
                                         List<MedicamentoEnNota> medicamentos, QuimioResponse cicloAnterior) {
    }

    public record EstadoQuimio(boolean intelisisDisponible, boolean leyendo, LocalDateTime ultimaLectura, String estatus,
                               String error, int solicitudes) {
    }
}
