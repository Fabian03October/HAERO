package com.example.backend.farmacia.repository;

import com.example.backend.farmacia.entity.Entrega;
import com.example.backend.farmacia.entity.Receta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    List<Entrega> findByRecetaIdOrderByFechaAsc(Long recetaId);

    List<Entrega> findByFechaBetweenOrderByFechaAsc(LocalDateTime desde, LocalDateTime hasta);

    List<Entrega> findTop20ByRecetaPacienteExpedienteOrderByFechaDesc(String expediente);

    List<Entrega> findTop20ByRecetaPacienteCurpIgnoreCaseOrderByFechaDesc(String curp);

    List<Entrega> findTop20ByRecetaPacienteNombreContainingIgnoreCaseOrderByFechaDesc(String nombre);

    // ---------- Baja por volumen de un área (Quimioterapia, Quirófano) ----------

    /** Pacientes (recetas) y entregas de un área que aún no están en una baja por volumen, hasta el día indicado. */
    interface Pendientes {
        long getPacientes();
        long getEntregas();
    }

    @Query("""
            SELECT COUNT(DISTINCT e.receta.id) AS pacientes, COUNT(e) AS entregas FROM Entrega e
            WHERE e.receta.folio LIKE :prefijo AND e.fecha < :antesDe AND e.bajaVolumenFolio IS NULL
            """)
    Pendientes pendientesDeBaja(String prefijo, LocalDateTime antesDe);

    /** Recetas del área con entregas sin baja que todavía no tienen hoja de baja. */
    @Query("""
            SELECT DISTINCT e.receta FROM Entrega e
            WHERE e.receta.folio LIKE :prefijo AND e.fecha < :antesDe AND e.bajaVolumenFolio IS NULL
              AND NOT EXISTS (SELECT 1 FROM FarmaciaHojaBaja h WHERE h.receta = e.receta)
            """)
    List<Receta> recetasSinHoja(String prefijo, LocalDateTime antesDe);

    @Query("""
            SELECT COUNT(e) FROM Entrega e WHERE e.bajaVolumenFolio IS NULL
              AND EXISTS (SELECT 1 FROM FarmaciaHojaBaja h WHERE h.receta = e.receta AND h.central = :central)
            """)
    long sinBajaConHoja(String central);

    /** Pasa a la baja por volumen todas las entregas sin baja cuyas recetas ya tienen hoja de esa área. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Entrega e SET e.bajaVolumenFolio = :folio WHERE e.bajaVolumenFolio IS NULL
              AND EXISTS (SELECT 1 FROM FarmaciaHojaBaja h WHERE h.receta = e.receta AND h.central = :central)
            """)
    int asignarBajaVolumen(Integer folio, String central);
}
