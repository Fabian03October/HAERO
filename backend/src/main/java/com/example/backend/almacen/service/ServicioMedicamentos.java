package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.entity.MedicamentoSeguridad;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import com.example.backend.almacen.repository.MedicamentoSeguridadRepository;
import com.example.backend.entity.Usuario;
import com.example.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogo de medicamentos (solo consulta). Los datos vienen de Intelisis; HAERO solo
 * agrega los codigos de barras. Una clave esta "dada de alta en HAERO" cuando ya tiene
 * al menos un codigo de barras: solo esas entran al inventario y se pueden despachar.
 * Tambien agrega las marcas de seguridad del paciente: alto riesgo y red fria.
 */
@Service
public class ServicioMedicamentos {

    private final MedicamentoRepository medicamentoRepository;
    private final CodigoBarrasRepository codigoBarrasRepository;
    private final MedicamentoSeguridadRepository seguridadRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicioMedicamentos(MedicamentoRepository medicamentoRepository, CodigoBarrasRepository codigoBarrasRepository,
                                MedicamentoSeguridadRepository seguridadRepository, UsuarioRepository usuarioRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.codigoBarrasRepository = codigoBarrasRepository;
        this.seguridadRepository = seguridadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public MedicamentoResponse obtener(String clave) {
        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        return aRespuesta(medicamento, codigosPorClave(), seguridadRepository.findById(clave).orElse(null));
    }

    public List<MedicamentoResponse> listar() {
        Map<String, Integer> codigos = codigosPorClave();
        Map<String, MedicamentoSeguridad> seguridad = seguridadRepository.findAll().stream()
                .collect(Collectors.toMap(MedicamentoSeguridad::getClave, Function.identity()));
        return medicamentoRepository.findAll().stream()
                .map(m -> aRespuesta(m, codigos, seguridad.get(m.getClave())))
                .toList();
    }

    /**
     * Farmacia marca o desmarca una clave como de alto riesgo o de red fria. altoRiesgo null
     * vuelve a usar lo que dice Intelisis.
     */
    @Transactional
    public MedicamentoResponse actualizarSeguridad(String clave, Boolean altoRiesgo, boolean redFria, String nombreUsuario) {
        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        MedicamentoSeguridad seguridad = seguridadRepository.findById(clave).orElseGet(() -> {
            MedicamentoSeguridad nueva = new MedicamentoSeguridad();
            nueva.setClave(clave);
            return nueva;
        });
        seguridad.setAltoRiesgo(altoRiesgo);
        seguridad.setRedFria(redFria);
        seguridad.setUsuario(usuario);
        seguridad.setActualizadoEn(LocalDateTime.now());
        seguridadRepository.save(seguridad);
        return aRespuesta(medicamento, codigosPorClave(), seguridad);
    }

    /** Codigos de barras por clave, en una sola consulta. */
    private Map<String, Integer> codigosPorClave() {
        Map<String, Integer> codigos = new HashMap<>();
        for (Object[] fila : codigoBarrasRepository.contarPorClave()) {
            codigos.put((String) fila[0], ((Number) fila[1]).intValue());
        }
        return codigos;
    }

    private MedicamentoResponse aRespuesta(Medicamento m, Map<String, Integer> codigos, MedicamentoSeguridad seguridad) {
        int codigosBarras = codigos.getOrDefault(m.getClave(), 0);
        boolean altoRiesgoIntelisis = "ALTO".equalsIgnoreCase(m.getRiesgo());
        Boolean altoRiesgoMarcado = seguridad == null ? null : seguridad.getAltoRiesgo();
        return MedicamentoResponse.builder()
                .clave(m.getClave())
                .nombreGenerico(m.getNombreGenerico())
                .presentacion(m.getPresentacion())
                .piezasPorCaja(m.getPiezasPorCaja())
                .descripcion(m.getDescripcion())
                .formaFarmaceutica(m.getFormaFarmaceutica())
                .concentracion(m.getConcentracion())
                .grupoTerapeutico(m.getGrupoTerapeutico())
                .cuadroBasico(m.getCuadroBasico())
                .estatus(m.getEstatus())
                .claveCucop(m.getClaveCucop())
                .unidadCompra(m.getUnidadCompra())
                .unidadMinima(m.getUnidadMinima())
                .riesgo(m.getRiesgo())
                .principalIndicacion(m.getPrincipalIndicacion())
                .contraindicaciones(m.getContraindicaciones())
                .descripcionCompleta(m.getDescripcionCompleta())
                .origen(m.getOrigen())
                .sincronizadoEn(m.getSincronizadoEn())
                .dadoDeAlta(codigosBarras > 0)
                .codigosBarras(codigosBarras)
                .altoRiesgo(altoRiesgoMarcado != null ? altoRiesgoMarcado : altoRiesgoIntelisis)
                .altoRiesgoIntelisis(altoRiesgoIntelisis)
                .altoRiesgoMarcado(altoRiesgoMarcado)
                .redFria(seguridad != null && seguridad.isRedFria())
                .build();
    }
}
