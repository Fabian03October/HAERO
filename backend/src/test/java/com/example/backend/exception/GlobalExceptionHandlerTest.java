package com.example.backend.exception;

import com.example.backend.almacen.entity.Existencia;
import org.junit.jupiter.api.Test;
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
}
