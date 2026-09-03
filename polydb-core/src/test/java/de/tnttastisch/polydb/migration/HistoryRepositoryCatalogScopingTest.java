package de.tnttastisch.polydb.migration;

import com.zaxxer.hikari.HikariDataSource;
import de.tnttastisch.polydb.dialect.H2Dialect;
import de.tnttastisch.polydb.migration.core.MigrationContext;
import de.tnttastisch.polydb.migration.core.MigrationRunner;
import de.tnttastisch.polydb.migration.history.HistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the schema-history lookup leaking across databases.
 *
 * <p>On MariaDB/MySQL the JDBC {@code catalog} is the database and the JDBC user (e.g. {@code root})
 * can see every database on the server, so an unscoped {@code DatabaseMetaData.getTables(null, null, …)}
 * finds a same-named {@code polydb_schema_history} in <em>another</em> database. PolyDB would then skip
 * creating the table in the <em>current</em> database and later fail with
 * {@code Table '<current-db>.polydb_schema_history' doesn't exist}, because all DDL/DML uses unqualified
 * names and resolves against the current database.</p>
 *
 * <p>H2 reproduces the same class of bug across two <em>schemas</em> within one database: an unscoped
 * lookup (schemaPattern {@code null}) matches tables in any schema, while unqualified statements target
 * the connection's current schema. {@code DB_A} plays the role of the "other database" that already has
 * a history table; the PolyDB {@link javax.sql.DataSource} operates in {@code DB_B}.</p>
 */
class HistoryRepositoryCatalogScopingTest {

    private static final String MIGRATION_PACKAGE = "de.tnttastisch.polydb.testmigrations";

    /** Kept open so the shared in-memory database survives between connections, and used for assertions. */
    private Connection keepAlive;
    /** PolyDB's DataSource, pinned to schema {@code DB_B}. */
    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        String baseUrl = "jdbc:h2:mem:crossdb_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";

        keepAlive = DriverManager.getConnection(baseUrl, "sa", "");
        try (Statement st = keepAlive.createStatement()) {
            st.execute("CREATE SCHEMA DB_A");
            st.execute("CREATE SCHEMA DB_B");
            // A "foreign" history table in DB_A, using the original (pre-extended) column set. Under the
            // old, unscoped lookup this table would be picked up while operating in DB_B, triggering an
            // ALTER TABLE against the (non-existent) DB_B.polydb_schema_history — the reported failure.
            st.execute("CREATE TABLE DB_A.polydb_schema_history (" +
                    "version VARCHAR(50) PRIMARY KEY, " +
                    "description VARCHAR(200), " +
                    "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "success BOOLEAN)");
        }

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(baseUrl + ";SCHEMA=DB_B");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
    }

    @AfterEach
    void tearDown() throws SQLException {
        dataSource.close();
        keepAlive.close();
    }

    @Test
    void createsHistoryTableInCurrentSchemaDespiteSameNamedTableInAnotherSchema() throws SQLException {
        assertThat(historyTableExistsIn("DB_A")).isTrue();
        assertThat(historyTableExistsIn("DB_B")).isFalse();

        new HistoryRepository(dataSource, new H2Dialect()).ensureHistoryTable();

        // The table is created in the active schema (DB_B), not assumed to exist because DB_A has one.
        assertThat(historyTableExistsIn("DB_B")).isTrue();
        // It has the full extended schema — proving CREATE TABLE ran here rather than a no-op upgrade
        // that inspected DB_A's columns.
        assertThat(columnsOf("DB_B")).contains("STATUS", "CHECKSUM", "ERROR_MESSAGE", "POLYDB_VERSION");
        // DB_A's foreign table is left untouched (still the original four columns, no extended ones).
        assertThat(columnsOf("DB_A")).doesNotContain("STATUS", "CHECKSUM");
    }

    @Test
    void runsMigrationsAndRecordsHistoryInCurrentSchema() throws SQLException {
        new MigrationRunner(new MigrationContext(dataSource, new H2Dialect())).run(MIGRATION_PACKAGE);

        // Migrations and history recording complete against DB_B...
        assertThat(count("SELECT COUNT(*) FROM DB_B.accounts")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM DB_B.polydb_schema_history")).isEqualTo(2);
        // ...and DB_A's history table was never treated as ours (no rows written).
        assertThat(count("SELECT COUNT(*) FROM DB_A.polydb_schema_history")).isEqualTo(0);
    }

    private boolean historyTableExistsIn(String schema) throws SQLException {
        return count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_NAME = 'POLYDB_SCHEMA_HISTORY'") > 0;
    }

    private Set<String> columnsOf(String schema) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_NAME = 'POLYDB_SCHEMA_HISTORY'")) {
            while (rs.next()) {
                columns.add(rs.getString(1).toUpperCase());
            }
        }
        return columns;
    }

    private int count(String sql) throws SQLException {
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
