package com.example.backend.almacen.repository;

import com.example.backend.almacen.dto.DocumentoExternoResponse;
import com.example.backend.almacen.entity.DocumentoExterno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentoExternoRepository extends JpaRepository<DocumentoExterno, Long> {

    /** Datos de los documentos sin leer el contenido del PDF. */
    @Query("select new com.example.backend.almacen.dto.DocumentoExternoResponse("
            + "d.id, d.expediente.id, d.tipo, d.nombreArchivo, d.tamano, d.fecha, d.usuario.nombreCompleto) "
            + "from DocumentoExterno d where d.expediente.id in :expedientes order by d.fecha")
    List<DocumentoExternoResponse> resumenes(@Param("expedientes") Collection<Long> expedientes);

    Optional<DocumentoExterno> findByIdAndExpedienteId(Long id, Long expedienteId);
}
