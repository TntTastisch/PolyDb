package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.Connection;

public class MigrationContext {

    private final DataSource dataSource;
    private final Dialect dialect;

    public MigrationContext(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public Connection getConnection() throws java.sql.SQLException {
        return dataSource.getConnection();
    }
}
