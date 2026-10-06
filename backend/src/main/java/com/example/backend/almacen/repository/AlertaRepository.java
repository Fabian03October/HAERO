package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    // List, no Optional: no hay restriccion unica en la base que impida que
    // existan dos alertas abiertas para la misma clave/lote+tipo (p. ej. por
    // una carrera entre dos instancias evaluando al mismo tiempo); el
    // servicio se queda con la mas antigua y cierra el resto (autocuracion).
    List<Alerta> findByMedicamentoClaveAndTipoAndFechaFinIsNullOrderByFechaInicioAsc(String medicamentoClave, String tipo);

    List<Alerta> findByLoteIdAndTipoAndFechaFinIsNullOrderByFechaInicioAsc(Long loteId, String tipo);

    List<Alerta> findByFechaFinIsNullOrderByFechaInicioDesc();

    List<Alerta> findByFechaFinIsNotNullOrderByFechaFinDesc();
}
