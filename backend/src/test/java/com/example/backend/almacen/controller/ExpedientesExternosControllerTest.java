package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.ExpedienteExternoResponse;
import com.example.backend.almacen.dto.RegistrarExpedienteRequest;
import com.example.backend.almacen.entity.DocumentoExterno;
import com.example.backend.almacen.service.ServicioExpedientesExternos;
import com.example.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que las peticiones multipart (parte "datos" en JSON + parte "documento"
 * en PDF), tal como las arma el frontend, llegan bien al servicio.
 */
@ExtendWith(MockitoExtension.class)
class ExpedientesExternosControllerTest {

    private static final byte[] PDF = "%PDF-1.7 prueba".getBytes();

    @Mock
    private ServicioExpedientesExternos servicio;

    private MockMvc mvc;

    private final UsernamePasswordAuthenticationToken almacen = new UsernamePasswordAuthenticationToken("almacen", null, List.of());

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.standaloneSetup(new ExpedientesExternosController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registrar_recibeLosDatosEnJsonYElPdf() throws Exception {
        when(servicio.registrar(any(), any(), eq("almacen"))).thenReturn(respuesta());
        MockMultipartFile datos = new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"tipo\":\"PRESTAMO\",\"sentido\":\"SALIDA\",\"institucion\":\"Hospital General\","
                        + "\"cajas\":20,\"existenciaId\":40,\"fechaLimite\":\"2026-11-30\",\"observaciones\":\"Oficio 123\"}").getBytes());
        MockMultipartFile documento = new MockMultipartFile("documento", "solicitud.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        mvc.perform(multipart("/api/almacen/expedientes-externos").file(datos).file(documento).principal(almacen))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estatus").value("ACTIVO"));

        ArgumentCaptor<RegistrarExpedienteRequest> request = ArgumentCaptor.forClass(RegistrarExpedienteRequest.class);
        ArgumentCaptor<MultipartFile> archivo = ArgumentCaptor.forClass(MultipartFile.class);
        verify(servicio).registrar(request.capture(), archivo.capture(), eq("almacen"));
        assertThat(request.getValue().getTipo()).isEqualTo("PRESTAMO");
        assertThat(request.getValue().getExistenciaId()).isEqualTo(40L);
        assertThat(request.getValue().getFechaLimite()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(request.getValue().getObservaciones()).isEqualTo("Oficio 123");
        assertThat(archivo.getValue().getOriginalFilename()).isEqualTo("solicitud.pdf");
    }

    @Test
    void registrar_sinParteDatos_responde400ConMensaje() throws Exception {
        MockMultipartFile documento = new MockMultipartFile("documento", "solicitud.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        mvc.perform(multipart("/api/almacen/expedientes-externos").file(documento).principal(almacen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void descargar_devuelveElPdfConSuNombre() throws Exception {
        DocumentoExterno documento = new DocumentoExterno();
        documento.setNombreArchivo("oficio préstamo.pdf");
        documento.setContenido(PDF);
        when(servicio.documento(7L, 3L)).thenReturn(documento);

        mvc.perform(get("/api/almacen/expedientes-externos/7/documentos/3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().bytes(PDF));
    }

    private static ExpedienteExternoResponse respuesta() {
        return new ExpedienteExternoResponse(7L, "PRESTAMO", "SALIDA", "Hospital General", "010.000.6059.01-1", "Lactulosa",
                20, 0, null, LocalDate.of(2026, 11, 30), null, "ACTIVO", "ACTIVO", "Oficio 123", "Encargada de Almacén",
                List.of(), List.of());
    }
}
