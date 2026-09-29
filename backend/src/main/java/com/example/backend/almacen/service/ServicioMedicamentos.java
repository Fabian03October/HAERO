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
        return new MedicamentoResponse(m.getClave(), m.getNombreGenerico(), m.getPresentacion(), m.getPiezasPorCaja(), m.getDescripcion());
    }
}
