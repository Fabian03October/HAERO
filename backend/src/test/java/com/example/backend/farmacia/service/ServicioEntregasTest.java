package com.example.backend.farmacia.service;

import com.example.backend.almacen.entity.Medicamento;
import com.example.backend.farmacia.entity.ClaveReceta;
import com.example.backend.farmacia.entity.LoteFarmacia;
import com.example.backend.farmacia.entity.Receta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU28 a HU30: regla FEFO al escanear en Farmacia y estado de la receta.
 */
class ServicioEntregasTest {

    private static final String CLAVE = "010.000.0101.00";

    private Medicamento medicamento;
    private LoteFarmacia proximo;
    private LoteFarmacia posterior;
    private LoteFarmacia vencido;

    @BeforeEach
    void configurar() {
        medicamento = new Medicamento();
        medicamento.setClave(CLAVE);
        proximo = lote(1L, "A", LocalDate.now().plusMonths(2), 30);
        posterior = lote(2L, "B", LocalDate.now().plusMonths(8), 80);
        vencido = lote(3L, "V", LocalDate.now().minusDays(1), 10);
    }

    @Test
    void sinLoteEnElCodigo_tomaElDeCaducidadMasProxima() {
        LoteFarmacia elegido = ServicioEntregas.elegirLote(CLAVE, null, List.of(posterior, vencido, proximo), new HashMap<>());

        assertThat(elegido.getNumeroLote()).isEqualTo("A");
    }

    @Test
    void cajaDeUnLotePosterior_seBloqueaYDiceCualDebeSalir() {
        assertThatThrownBy(() -> ServicioEntregas.elegirLote(CLAVE, "b", List.of(proximo, posterior), new HashMap<>()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Debe salir primero el lote A")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cuandoElLoteProximoYaSeTomoEnLaMismaEntrega_elSiguienteSaleSinBloqueo() {
        Map<Long, Integer> restante = new HashMap<>(Map.of(1L, 0, 2L, 80));

        LoteFarmacia elegido = ServicioEntregas.elegirLote(CLAVE, "B", List.of(proximo, posterior), restante);

        assertThat(elegido.getNumeroLote()).isEqualTo("B");
    }

    @Test
    void loteVencido_nuncaSeEntrega() {
        assertThatThrownBy(() -> ServicioEntregas.elegirLote(CLAVE, "V", List.of(vencido, proximo), new HashMap<>()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("venció");
    }

    @Test
    void mismaCaducidad_noBloquea() {
        LoteFarmacia gemelo = lote(4L, "C", proximo.getCaducidad(), 5);

        LoteFarmacia elegido = ServicioEntregas.elegirLote(CLAVE, "C", List.of(proximo, gemelo), new HashMap<>());

        assertThat(elegido.getNumeroLote()).isEqualTo("C");
    }

    @Test
    void sinExistenciaDisponible_avisaAlDespachador() {
        assertThatThrownBy(() -> ServicioEntregas.elegirLote(CLAVE, null, List.of(vencido), new HashMap<>()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No hay existencia disponible");
    }

    @Test
    void estadoDeLaReceta_surtidaParcialONoDispensada() {
        Receta receta = new Receta();
        ClaveReceta a = clave(receta, 10, 10, false);
        ClaveReceta b = clave(receta, 5, 0, true);
        assertThat(ServicioEntregas.estado(receta)).isEqualTo(Receta.PARCIAL);

        a.setPiezasEntregadas(0);
        a.setNoDispensada(true);
        assertThat(ServicioEntregas.estado(receta)).isEqualTo(Receta.NO_DISPENSADA);

        a.setPiezasEntregadas(10);
        b.setPiezasEntregadas(5);
        assertThat(ServicioEntregas.estado(receta)).isEqualTo(Receta.SURTIDA);
    }

    @Test
    void loteFefoDelInventario_ignoraVencidosYLotesSinPiezas() {
        LoteFarmacia vacio = lote(5L, "Z", LocalDate.now().plusDays(10), 0);

        Map<String, Long> fefo = ServicioInventarioFarmacia.lotesFefo(List.of(posterior, vencido, vacio, proximo));

        assertThat(fefo).containsEntry(CLAVE, 1L);
    }

    private LoteFarmacia lote(Long id, String numero, LocalDate caducidad, int piezas) {
        LoteFarmacia lote = new LoteFarmacia();
        lote.setId(id);
        lote.setMedicamento(medicamento);
        lote.setNumeroLote(numero);
        lote.setCaducidad(caducidad);
        lote.setPiezas(piezas);
        return lote;
    }

    private ClaveReceta clave(Receta receta, int indicadas, int entregadas, boolean noDispensada) {
        ClaveReceta clave = new ClaveReceta();
        clave.setReceta(receta);
        clave.setMedicamento(medicamento);
        clave.setPiezasIndicadas(indicadas);
        clave.setPiezasEntregadas(entregadas);
        clave.setNoDispensada(noDispensada);
        receta.getClaves().add(clave);
        return clave;
    }
}
