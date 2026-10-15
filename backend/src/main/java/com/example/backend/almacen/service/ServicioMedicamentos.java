package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import com.example.backend.almacen.repository.MedicamentoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogo de medicamentos (solo consulta). Los datos vienen de Intelisis; HAERO solo
 * agrega los codigos de barras. Una clave esta "dada de alta en HAERO" cuando ya tiene
 * al menos un codigo de barras: solo esas entran al inventario y se pueden despachar.
 */
@Service
public class ServicioMedicamentos {

    private final MedicamentoRepository medicamentoRepository;
    private final CodigoBarrasRepository codigoBarrasRepository;

    public ServicioMedicamentos(MedicamentoRepository medicamentoRepository, CodigoBarrasRepository codigoBarrasRepository) {
        this.medicamentoRepository = medicamentoRepository;
        this.codigoBarrasRepository = codigoBarrasRepository;
    }

    public MedicamentoResponse obtener(String clave) {
        Medicamento medicamento = medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado"));
        return aRespuesta(medicamento, codigosPorClave());
    }

    public List<MedicamentoResponse> listar() {
        Map<String, Integer> codigos = codigosPorClave();
        return medicamentoRepository.findAll().stream().map(m -> aRespuesta(m, codigos)).toList();
    }

    /** Codigos de barras por clave, en una sola consulta. */
    private Map<String, Integer> codigosPorClave() {
        Map<String, Integer> codigos = new HashMap<>();
        for (Object[] fila : codigoBarrasRepository.contarPorClave()) {
            codigos.put((String) fila[0], ((Number) fila[1]).intValue());
        }
        return codigos;
    }

    private MedicamentoResponse aRespuesta(Medicamento m, Map<String, Integer> codigos) {
        int codigosBarras = codigos.getOrDefault(m.getClave(), 0);
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
                .build();
    }
}
