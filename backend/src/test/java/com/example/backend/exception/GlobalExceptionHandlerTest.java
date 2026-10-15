package com.example.backend.exception;

import com.example.backend.almacen.entity.Existencia;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void edicionSimultanea_responde409ConMensajeParaReintentar() {
        var respuesta = new GlobalExceptionHandler()
                .manejarEdicionSimultanea(new ObjectOptimisticLockingFailureException(Existencia.class, 40L));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).containsEntry("mensaje",
                "Otra persona registró un movimiento sobre estos datos al mismo tiempo. Vuelve a cargar la información y repite la operación.");
    }

    @Test
    void registroDeIntelisisRecibidoDosVeces_respondeConMensajeClaro() {
        var causa = new java.sql.SQLException("Cannot insert duplicate key row in object 'dbo.lote' with unique index 'UX_lote_registro_intelisis'.");
        var respuesta = new GlobalExceptionHandler()
                .manejarViolacionIntegridad(new DataIntegrityViolationException("duplicado", causa));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("mensaje")).startsWith("Ese registro de Intelisis ya se recibió en HAERO");
    }
}
