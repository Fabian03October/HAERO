package com.example.backend.almacen.repository;

import com.example.backend.almacen.entity.AlmacenBajaDefinitiva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AlmacenBajaDefinitivaRepository extends JpaRepository<AlmacenBajaDefinitiva, Long> {

    /** Datos de una baja para listarla, sin el PDF (el acta se descarga aparte). */
    interface Resumen {
        Long getId();
        String getFolioActa();
        String getEmpresa();
        LocalDate getFechaRecoleccion();
        String getObservaciones();
        String getDocumentoNombre();
        LocalDateTime getFecha();
        String getUsuario();
    }

    @Query("""
            SELECT b.id AS id, b.folioActa AS folioActa, b.empresa AS empresa, b.fechaRecoleccion AS fechaRecoleccion,
                   b.observaciones AS observaciones, b.documentoNombre AS documentoNombre, b.fecha AS fecha, u.nombreCompleto AS usuario
            FROM AlmacenBajaDefinitiva b JOIN b.usuario u ORDER BY b.fecha DESC
            """)
    List<Resumen> resumenes();
}
