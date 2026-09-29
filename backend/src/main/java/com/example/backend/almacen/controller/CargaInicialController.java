package com.example.backend.almacen.controller;

import com.example.backend.almacen.dto.CargaInicialResponse;
import com.example.backend.almacen.service.ServicioCargaInicial;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/almacen/carga-inicial")
public class CargaInicialController {

    private final ServicioCargaInicial servicioCargaInicial;

    public CargaInicialController(ServicioCargaInicial servicioCargaInicial) {
        this.servicioCargaInicial = servicioCargaInicial;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaInicialResponse> cargar(@RequestParam("archivo") MultipartFile archivo,
                                                        Authentication authentication) {
        CargaInicialResponse respuesta = servicioCargaInicial.cargar(archivo, authentication.getName());
        HttpStatus status = respuesta.isExito() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(respuesta);
    }
}
