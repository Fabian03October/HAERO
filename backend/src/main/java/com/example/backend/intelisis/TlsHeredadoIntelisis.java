package com.example.backend.intelisis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import java.security.Security;

/**
 * El SQL Server de Intelisis (2012 sin actualizar) solo negocia TLS 1.0, que
 * Java 21 bloquea por defecto. Con intelisis.tls-heredado=true se vuelven a
 * permitir TLS 1.0/1.1, el intercambio de llaves RSA y las firmas SHA1 que usan
 * esos servidores viejos; siguen bloqueados SSLv3, RC4, DES, 3DES, anon, NULL,
 * MD5withRSA y llaves cortas.
 *
 * Corre en cuanto Spring termina de leer la configuracion y antes de crear
 * cualquier conexion: Java lee esta lista una sola vez, en la primera conexion
 * cifrada. Apagado por defecto. Quitar cuando Sistemas habilite TLS 1.2 en el
 * servidor de Intelisis.
 */
public class TlsHeredadoIntelisis implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(TlsHeredadoIntelisis.class);
    private static final String ALGORITMOS_DESHABILITADOS =
            "SSLv3, DTLSv1.0, RC4, DES, MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL, ECDH";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        if (event.getEnvironment().getProperty("intelisis.tls-heredado", Boolean.class, false)) {
            Security.setProperty("jdk.tls.disabledAlgorithms", ALGORITMOS_DESHABILITADOS);
            log.warn("TLS 1.0 permitido para conectar con Intelisis (intelisis.tls-heredado=true). Pedir a Sistemas TLS 1.2.");
        }
    }
}
