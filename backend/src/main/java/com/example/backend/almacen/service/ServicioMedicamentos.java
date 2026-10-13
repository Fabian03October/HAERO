package com.example.backend.almacen.service;

import com.example.backend.almacen.dto.CrearMedicamentoRequest;
import com.example.backend.almacen.dto.MedicamentoResponse;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.MedicamentoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ServicioMedicamentos {

    private final MedicamentoRepository medicamentoRepository;

    public ServicioMedicamentos(MedicamentoRepository medicamentoRepository) {
        this.medicamentoRepository = medicamentoRepository;
    }

    public MedicamentoResponse crear(CrearMedicamentoRequest request) {
        if (medicamentoRepository.existsById(request.getClave())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un medicamento con esa clave");
        }

        Medicamento medicamento = new Medicamento();
        medicamento.setClave(request.getClave());
        medicamento.setNombreGenerico(request.getNombreGenerico());
        medicamento.setPresentacion(request.getPresentacion());
        medicamento.setPiezasPorCaja(request.getPiezasPorCaja());
        medicamento.setDescripcion(request.getDescripcion());

        return aRespuesta(medicamentoRepository.save(medicamento));
    }

    public MedicamentoResponse obtener(String clave) {
        return aRespuesta(medicamentoRepository.findById(clave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicamento no encontrado")));
    }

    public List<MedicamentoResponse> listar() {
        return medicamentoRepository.findAll().stream().map(this::aRespuesta).toList();
    }

    private MedicamentoResponse aRespuesta(Medicamento m) {
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
                .build();
    }
}
