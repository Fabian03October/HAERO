package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.ConteoInicialRenglon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConteoInicialRenglonRepository extends JpaRepository<ConteoInicialRenglon, Long> {

    /** Los renglones de un área con su medicamento y quién registró y contó (una sola consulta). */
    @EntityGraph(attributePaths = {"medicamento", "registradoPor", "contadoPor"})
    List<ConteoInicialRenglon> findByAreaOrderByUbicacionAscMedicamentoClaveAscLoteAsc(String area);

    @EntityGraph(attributePaths = {"medicamento", "registradoPor", "contadoPor"})
    Optional<ConteoInicialRenglon> findByIdAndArea(Long id, String area);

    @Query("SELECT DISTINCT r.caducidad FROM ConteoInicialRenglon r WHERE r.area = :area AND r.medicamento.clave = :clave AND r.lote = :lote")
    List<LocalDate> caducidadesDelLote(String area, String clave, String lote);

    /** El renglón de ese lote en esa ubicación (null = sin estante). */
    @Query("""
            SELECT r.id FROM ConteoInicialRenglon r WHERE r.area = :area AND r.medicamento.clave = :clave AND r.lote = :lote
              AND (r.ubicacion = :ubicacion OR (r.ubicacion IS NULL AND :ubicacion IS NULL))
            """)
    List<Long> idsDelLoteEn(String area, String clave, String lote, String ubicacion);

    @Query("SELECT r.id FROM ConteoInicialRenglon r WHERE r.area = :area AND (r.ubicacion = :ubicacion OR (r.ubicacion IS NULL AND :ubicacion IS NULL))")
    List<Long> idsDelEstante(String area, String ubicacion);

    /** Un código ya usado en otro renglón del conteo (para no volver a pedir la clave). */
    @Query("SELECT r.medicamento.clave FROM ConteoInicialRenglon r WHERE r.codigo = :codigo")
    List<String> clavesDelCodigo(String codigo);
}
