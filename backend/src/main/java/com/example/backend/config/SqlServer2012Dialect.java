package com.example.backend.config;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.SQLServerDialect;

/**
 * Fija el dialecto de Hibernate en SQL Server 2012 (version de motor 11.0),
 * sin importar la version real del servidor al que nos conectemos (por ejemplo,
 * SQL Server 2019 Express en desarrollo). Asi Hibernate nunca genera SQL que
 * dependa de funciones agregadas despues de 2012 (STRING_AGG, JSON, etc.),
 * y el SQL generado en desarrollo es el mismo que correra contra el
 * SQL Server 2012 de produccion del hospital.
 */
public class SqlServer2012Dialect extends SQLServerDialect {

    public SqlServer2012Dialect() {
        super(DatabaseVersion.make(11, 0));
    }
}
