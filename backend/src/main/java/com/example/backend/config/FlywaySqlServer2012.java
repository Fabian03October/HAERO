package com.example.backend.config;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.database.sqlserver.SQLServerDatabase;
import org.flywaydb.database.sqlserver.SQLServerDatabaseType;

/**
 * El SQL Server del servidor del hospital es 2012 (motor 11.0) y Flyway Community 12 exige 2017+.
 * Este tipo de base (registrado en META-INF/services) gana por prioridad al de Flyway y solo pide 2012,
 * la misma versión que fija {@link SqlServer2012Dialect}. Las migraciones de HAERO no usan nada posterior a 2012.
 * shortcut: Flyway no prueba su versión Community contra 2012; si TIC sube a SQL Server 2017+, borrar esta clase.
 */
public class FlywaySqlServer2012 extends SQLServerDatabaseType {

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Database createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
                                   StatementInterceptor statementInterceptor) {
        return new SQLServerDatabase(configuration, jdbcConnectionFactory, statementInterceptor) {
            @Override
            public void ensureSupported(Configuration configuration) {
                ensureDatabaseIsRecentEnough("11.0");
            }
        };
    }
}
