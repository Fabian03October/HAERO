package com.example.backend;

import com.example.backend.intelisis.TlsHeredadoIntelisis;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication aplicacion = new SpringApplication(BackendApplication.class);
		// Debe registrarse antes de arrancar: ajusta TLS antes de la primera conexion.
		aplicacion.addListeners(new TlsHeredadoIntelisis());
		aplicacion.run(args);
	}

}
