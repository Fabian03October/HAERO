package com.example.backend.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Tamaño máximo de los archivos que se suben (PDF escaneados de préstamos y
 * transferencias). Spring permite 1 MB por defecto, poco para un escaneo.
 * Va aquí y no en application.properties para que aplique en todos los equipos.
 */
@Configuration
public class ArchivosConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(DataSize.ofMegabytes(11));
        return factory.createMultipartConfig();
    }
}
