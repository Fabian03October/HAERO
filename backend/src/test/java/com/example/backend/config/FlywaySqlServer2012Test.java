package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.junit.jupiter.api.Test;

class FlywaySqlServer2012Test {

    @Test
    void flywayUsaElTipoQueAceptaSqlServer2012() throws Exception {
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(meta.getDatabaseProductVersion()).thenReturn("11.00.2100");
        Connection conn = mock(Connection.class);
        when(conn.getMetaData()).thenReturn(meta);

        assertThat(DatabaseTypeRegister.getDatabaseTypeForConnection(conn, new FluentConfiguration()))
                .isInstanceOf(FlywaySqlServer2012.class);
    }
}
