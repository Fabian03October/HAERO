package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.PartidaEntrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PartidaEntregaRepository extends JpaRepository<PartidaEntrega, Long> {

    List<PartidaEntrega> findByLoteIdIn(Collection<Long> lotes);

    // Un paciente puede tener dos expedientes en Intelisis: se juntan los que comparten CURP en alguna receta
    // (con todas las recetas de esos expedientes, aunque alguna no trajera la CURP).
    String CURPS_DEL_EXPEDIENTE = "(SELECT r2.pacienteCurp FROM Receta r2 WHERE r2.pacienteExpediente = :exp AND r2.pacienteCurp IS NOT NULL)";
    String DEL_EXPEDIENTE = "(r.pacienteExpediente = :exp OR r.pacienteCurp IN " + CURPS_DEL_EXPEDIENTE
            + " OR r.pacienteExpediente IN (SELECT r3.pacienteExpediente FROM Receta r3 WHERE r3.pacienteCurp IN " + CURPS_DEL_EXPEDIENTE + "))";
    String DESDE_PARTIDA = "SELECT p FROM PartidaEntrega p JOIN p.entrega e JOIN e.receta r WHERE ";

    @Query(DESDE_PARTIDA + DEL_EXPEDIENTE + " ORDER BY e.fecha DESC")
    List<PartidaEntrega> delPacientePorExpediente(@Param("exp") String expediente);

    /** Por CURP, y también lo de sus expedientes aunque la receta no trajera la CURP. */
    @Query(DESDE_PARTIDA + "(UPPER(r.pacienteCurp) = UPPER(:curp) OR r.pacienteExpediente IN "
            + "(SELECT r2.pacienteExpediente FROM Receta r2 WHERE UPPER(r2.pacienteCurp) = UPPER(:curp))) ORDER BY e.fecha DESC")
    List<PartidaEntrega> delPacientePorCurp(@Param("curp") String curp);

    /** Partidas cuyo paciente trae esas dos palabras, sin distinguir mayúsculas ni acentos ("lopez" encuentra "LÓPEZ"). */
    @Query(value = "SELECT TOP 200 p.* FROM farmacia_entrega_partida p JOIN farmacia_entrega e ON e.id = p.entrega_id "
            + "JOIN farmacia_receta r ON r.id = e.receta_id "
            + "WHERE r.paciente_nombre COLLATE Latin1_General_CI_AI LIKE CONCAT('%', :a, '%') "
            + "AND r.paciente_nombre COLLATE Latin1_General_CI_AI LIKE CONCAT('%', :b, '%') ORDER BY e.fecha DESC", nativeQuery = true)
    List<PartidaEntrega> conPalabrasEnElNombre(@Param("a") String a, @Param("b") String b);

    List<PartidaEntrega> findByEntregaRecetaFolioIgnoreCaseOrderByEntregaFechaDesc(String folio);

    /** Entregas recientes de esas claves al paciente (para avisar antes de repetir). */
    @Query(DESDE_PARTIDA + DEL_EXPEDIENTE + " AND e.fecha >= :desde AND p.claveReceta.medicamento.clave IN :claves ORDER BY e.fecha DESC")
    List<PartidaEntrega> previasDelPaciente(@Param("exp") String expediente, @Param("desde") java.time.LocalDateTime desde,
                                            @Param("claves") Collection<String> claves);
}
