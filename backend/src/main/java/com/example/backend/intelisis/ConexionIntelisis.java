package com.example.backend.intelisis;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Conexion de SOLO LECTURA a la base de Intelisis (ERP del hospital).
 *
 * Esta apagada por defecto. Se enciende con variables de entorno, nunca con la
 * contrasena escrita en un archivo:
 *   INTELISIS_HABILITADO=true
 *   INTELISIS_URL=jdbc:sqlserver://servidor:1433;databaseName=...
 *   INTELISIS_USUARIO=...
 *   INTELISIS_PASSWORD=...
 *
 * A proposito NO se registra como bean DataSource: si lo fuera, Spring dejaria
 * de autoconfigurar la base principal (farmacia). Es un pool aparte y chico
 * (2 conexiones), marcado de solo lectura, con lectura sin bloqueos (READ
 * UNCOMMITTED, igual que WITH (NOLOCK)) para no frenar al ERP. La garantia real
 * de que no se escribe es el usuario de Intelisis, que solo tiene permiso de
 * lectura; el codigo ademas solo ejecuta SELECT.
 */
@Component
public class ConexionIntelisis {

    private static final Logger log = LoggerFactory.getLogger(ConexionIntelisis.class);
    private static final int SEGUNDOS_MAXIMOS_POR_CONSULTA = 120;

    private final boolean habilitado;
    private final String url;
    private final String usuario;
    private final String password;

    private volatile HikariDataSource dataSource;
    private volatile JdbcTemplate plantilla;

    public ConexionIntelisis(@Value("${intelisis.habilitado:false}") boolean habilitado,
                             @Value("${intelisis.url:}") String url,
                             @Value("${intelisis.usuario:}") String usuario,
                             @Value("${intelisis.password:}") String password) {
        this.habilitado = habilitado;
        this.url = url;
        this.usuario = usuario;
        this.password = password;
    }

    /** true si la integracion esta encendida y tiene los datos de conexion completos. */
    public boolean disponible() {
        return habilitado && !url.isBlank() && !usuario.isBlank() && !password.isBlank();
    }

    public String motivoNoDisponible() {
        if (!habilitado) {
            return "La integración con Intelisis está apagada (INTELISIS_HABILITADO no es true).";
        }
        return "Faltan variables de entorno de Intelisis (INTELISIS_URL, INTELISIS_USUARIO o INTELISIS_PASSWORD).";
    }

    /** Plantilla JDBC sobre Intelisis; vacia si la integracion no esta disponible. */
    public Optional<JdbcTemplate> plantilla() {
        if (!disponible()) {
            return Optional.empty();
        }
        if (plantilla == null) {
            synchronized (this) {
                if (plantilla == null) {
                    dataSource = crearPool();
                    JdbcTemplate nueva = new JdbcTemplate(dataSource);
                    nueva.setQueryTimeout(SEGUNDOS_MAXIMOS_POR_CONSULTA);
                    nueva.setFetchSize(1000);
                    plantilla = nueva;
                }
            }
        }
        return Optional.of(plantilla);
    }

    private HikariDataSource crearPool() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("intelisis-solo-lectura");
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setTransactionIsolation("TRANSACTION_READ_UNCOMMITTED");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15_000);
        config.setIdleTimeout(120_000);
        // Si Intelisis no responde al arrancar, HAERO arranca igual: el pool se
        // conecta hasta la primera sincronizacion.
        config.setInitializationFailTimeout(-1);
        log.info("Integración Intelisis encendida (solo lectura).");
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void cerrar() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
