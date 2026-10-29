package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.CodigoBarras;
import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.almacen.repository.CodigoBarrasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU42: Farmacia liga el código de una caja nueva a su clave una sola vez. */
@ExtendWith(MockitoExtension.class)
class ServicioInventarioFarmaciaCodigosTest {

    @Mock
    private CodigoBarrasRepository codigoBarrasRepository;

    private ServicioInventarioFarmacia servicio;

    @BeforeEach
    void configurar() {
        servicio = new ServicioInventarioFarmacia(null, null, null, null, null, null, codigoBarrasRepository);
    }

    private static Medicamento medicamento(String clave) {
        Medicamento m = new Medicamento();
        m.setClave(clave);
        m.setNombreGenerico("Omeprazol");
        return m;
    }

    @Test
    void unCodigoNuevo_seGuardaLigadoALaClave() {
        when(codigoBarrasRepository.findById("7501234567890")).thenReturn(Optional.empty());
        when(codigoBarrasRepository.findById("07501234567890")).thenReturn(Optional.empty());

        servicio.aprenderCodigo("7501234567890", medicamento("010.000.5187.00"));

        ArgumentCaptor<CodigoBarras> captor = ArgumentCaptor.forClass(CodigoBarras.class);
        verify(codigoBarrasRepository).save(captor.capture());
        assertThat(captor.getValue().getMedicamento().getClave()).isEqualTo("010.000.5187.00");
        assertThat(captor.getValue().getProveedor()).isEqualTo("Registrado por Farmacia");
    }

    @Test
    void unCodigoDeOtraClave_noSeReasigna() {
        CodigoBarras existente = new CodigoBarras();
        existente.setCodigo("07501234567890");
        existente.setMedicamento(medicamento("010.000.0104.00"));
        // La caja trae el EAN-13; Almacén lo guardó como GTIN-14 con el cero inicial.
        when(codigoBarrasRepository.findById("7501234567890")).thenReturn(Optional.empty());
        when(codigoBarrasRepository.findById("07501234567890")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.aprenderCodigo("7501234567890", medicamento("010.000.5187.00")))
                .hasMessageContaining("ya es de la clave 010.000.0104.00");
        verify(codigoBarrasRepository, never()).save(any());
    }

    @Test
    void sinCodigo_noHaceNada() {
        servicio.aprenderCodigo(null, medicamento("010.000.5187.00"));
        verify(codigoBarrasRepository, never()).save(any());
    }
}
