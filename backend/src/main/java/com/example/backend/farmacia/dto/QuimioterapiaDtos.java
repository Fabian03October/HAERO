package com.example.backend.farmacia.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * "lotes": lo escaneado, uno por lote con sus piezas (deben sumar "piezas"); si viene, manda sobre "lote".
     * "noSurtido": no se entrega (sin existencia, o no se maneja y el paciente lo compra) con su "motivoNoSurtido";
     * lo demás de la indicación sí se entrega.
     */
    public record RenglonQuimioRequest(String textoMedico, String clave, String alternativa, boolean usarAlternativa,
                                       String motivoAlternativa, String dosis, Integer piezas, String lote,
                                       BigDecimal dosisCantidad, String dosisUnidad, BigDecimal contenidoPorPieza,
                                       String destinoSobrante, List<LoteEscaneado> lotes, boolean noSurtido,
                                       String motivoNoSurtido) {
    }

    /** Piezas de un lote escaneadas para un renglón; "codigo" es el código de barras de la caja (se aprende si es nuevo). */
    public record LoteEscaneado(String lote, Integer piezas, String codigo) {
    }

    /** A qué clave pertenece un código de barras de caja (para el escaneo continuo del surtido). */
    public record CodigoMedicamento(String codigo, String clave, String nombre) {
    }

    /** justificacionSinIntelisis: si Intelisis no responde, cómo se confirmó al paciente (expediente físico). */
    public record RegistrarQuimioRequest(String folio, String expedienteSolicitud, String nombreSolicitud,
                                         PacienteQuimio paciente, boolean expedienteCorregido, String justificacionPaciente,
                                         String notaMedico, List<RenglonQuimioRequest> renglones,
                                         String justificacionSinIntelisis) {
    }

    /** nombreIndicada y nombreAlternativa sirven para repetir lo de un ciclo anterior. */
    public record RenglonQuimioResponse(String textoMedico, String claveIndicada, String claveAlternativa, String claveSurtida,
                                        String medicamento, String dosis, int piezas, String motivoAlternativa,
                                        BigDecimal dosisCantidad, String dosisUnidad, BigDecimal contenidoPorPieza,
                                        BigDecimal sobrante, String destinoSobrante, String nombreIndicada, String nombreAlternativa,
                                        boolean noSurtido, String motivoNoSurtido) {
    }

    public record QuimioResponse(Long id, String folio, int sesion, String expedienteSolicitud, String pacienteNombre,
                                 String pacienteExpediente, boolean expedienteCorregido, String justificacionPaciente,
                                 boolean sinIntelisis, String notaMedico, String folioEntrega, LocalDateTime fecha,
                                 String surtio, String verifico, List<RenglonQuimioResponse> renglones,
                                 List<PartidaEntregadaResponse> partidas) {
    }

    /** Sesiones anteriores de la misma solicitud (para avisar y validar que sea el mismo paciente). */
    /** El paciente no puede pasar: nueva fecha que dio Oncología Médica y el motivo. */
    public record ReprogramarQuimioRequest(LocalDate fechaNueva, String motivo) {
    }

    public record SesionesSolicitud(String folio, String pacienteExpediente, String pacienteNombre, List<QuimioResponse> sesiones) {
    }

    // ---------- Solicitudes de Oncología leídas de Intelisis ----------

    /** Una solicitud de la lista. folio es el "Folio" de la hoja impresa (ID_AuxTxSol en Intelisis). */
    /**
     * fechaRequerida = la que manda (la reprogramada si la hay); fechaOriginal = la de Intelisis cuando se
     * reprogramó (null si no) y motivoReprogramacion el porqué.
     */
    public record SolicitudQuimio(int folio, LocalDateTime fecha, LocalDateTime fechaRequerida, String estatus, String expediente,
                                  String paciente, String servicioSolicita, String medico, int sesionesSurtidas,
                                  LocalDateTime ultimaSesion, List<MedicamentoLista> medicamentos, int pendientes,
                                  LocalDateTime fechaOriginal, String motivoReprogramacion) {
        public SolicitudQuimio(int folio, LocalDateTime fecha, LocalDateTime fechaRequerida, String estatus, String expediente,
                               String paciente, String servicioSolicita, String medico, int sesionesSurtidas,
                               LocalDateTime ultimaSesion, List<MedicamentoLista> medicamentos, int pendientes) {
            this(folio, fecha, fechaRequerida, estatus, expediente, paciente, servicioSolicita, medico, sesionesSurtidas,
                    ultimaSesion, medicamentos, pendientes, null, null);
        }

        /** Sin los medicamentos de la nota (detalle o solicitud leída directo de Intelisis). */
        public SolicitudQuimio(int folio, LocalDateTime fecha, LocalDateTime fechaRequerida, String estatus, String expediente,
                               String paciente, String servicioSolicita, String medico, int sesionesSurtidas,
                               LocalDateTime ultimaSesion) {
            this(folio, fecha, fechaRequerida, estatus, expediente, paciente, servicioSolicita, medico, sesionesSurtidas,
                    ultimaSesion, List.of(), 0);
        }
    }

    /** Un medicamento de la nota en la lista de solicitudes: ENTREGADO, PENDIENTE (no se surtió) o POR_SURTIR. */
    public record MedicamentoLista(String nombre, String estado) {
    }

    /** Números del día: recuadros y pastillas de filtro de la lista. */
    public record ResumenQuimio(int porSurtir, int paraHoy, int conPendientes, int surtidasHoy) {
    }

    /**
     * Clave del catálogo que puede ser la que escribió el médico. contenidoPorPieza y unidadContenido salen
     * de la concentración del catálogo (ej. 300 mg); existencia son las piezas vigentes en Farmacia.
     */
    public record ClaveSugerida(String clave, String nombre, String forma, String concentracion, BigDecimal contenidoPorPieza,
                                String unidadContenido, int existencia, boolean usadaAntes) {
    }

    /** Un medicamento que HAERO encontró en la nota, con la dosis escrita junto a él y sus claves posibles. */
    /** soloSiNoHay: alternativa que solo se da si falta lo indicado ("EN CASO DE DESABASTO DE siNoHayDe DAR…").
     * solucion: diluyente o lavado (cloruro de sodio, glucosa…) junto por volumen; veces = bolsas. */
    public record MedicamentoEnNota(String texto, String farmaco, BigDecimal dosisCantidad, String dosisUnidad,
                                    List<ClaveSugerida> claves, boolean soloSiNoHay, String siNoHayDe,
                                    boolean solucion, int veces) {
    }

    /** La solicitud completa, la nota pasada a sugerencias y lo que se surtió al paciente la vez anterior. */
    public record SolicitudQuimioDetalle(SolicitudQuimio solicitud, String diagnostico, String notas,
                                         List<MedicamentoEnNota> medicamentos, QuimioResponse cicloAnterior) {
    }

    public record EstadoQuimio(boolean intelisisDisponible, boolean leyendo, LocalDateTime ultimaLectura, String estatus,
                               String error, int solicitudes) {
    }
}
