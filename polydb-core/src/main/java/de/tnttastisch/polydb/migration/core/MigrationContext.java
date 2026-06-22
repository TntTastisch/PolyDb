package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * The execution context handed to every {@link Migration}. It bundles the database access primitives
 * a migration needs: the pooled {@link DataSource}, freshly checked-out {@link Connection}s and the
 * active {@link Dialect} (so migrations can emit dialect-specific SQL). The same instance is reused
 * for every migration in a single run.
 */
public class MigrationContext {

    private final DataSource dataSource;
    private final Dialect dialect;

    public MigrationContext(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    /** The underlying connection pool; may be {@code null} for dialects without a JDBC data source. */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** The dialect of the target database, allowing migrations to branch on database-specific syntax. */
    public Dialect getDialect() {
        return dialect;
    }

    /**
     * Borrows a connection from the pool. The caller owns the returned connection and must close it
     * (ideally via try-with-resources) so it is returned to the pool.
     */
    public Connection getConnection() throws java.sql.SQLException {
        return dataSource.getConnection();
    }
}
