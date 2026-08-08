package de.tnttastisch.polydb.migration;

import com.zaxxer.hikari.HikariDataSource;
import de.tnttastisch.polydb.dialect.H2Dialect;
import de.tnttastisch.polydb.migration.core.MigrationContext;
import de.tnttastisch.polydb.migration.core.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@link MigrationRunner}: it discovers both a declarative {@code BaseMigration}
 * and a legacy raw-SQL {@code Migration} from the {@code testmigrations} package, applies them through
 * the shared executor, records rich history metadata, and is idempotent on a second run.
 */
class MigrationRunnerTest {

    private static final String MIGRATION_PACKAGE = "de.tnttastisch.polydb.testmigrations";

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:runner_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void appliesPlannedAndLegacyMigrationsAndRecordsRichHistory() throws SQLException {
        MigrationRunner runner = new MigrationRunner(new MigrationContext(dataSource, new H2Dialect()));
        runner.run(MIGRATION_PACKAGE);

        assertThat(count("SELECT COUNT(*) FROM accounts")).isEqualTo(2);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT version, type, status, checksum, execution_time_ms FROM polydb_schema_history ORDER BY version")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("001");
            assertThat(rs.getString("type")).isEqualTo("MANUAL");
            assertThat(rs.getString("status")).isEqualTo("SUCCESS");
            assertThat(rs.getString("checksum")).isNotBlank();
            assertThat(rs.getLong("execution_time_ms")).isGreaterThanOrEqualTo(0L);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("002");
            assertThat(rs.getString("type")).isEqualTo("LEGACY");
            assertThat(rs.getString("status")).isEqualTo("SUCCESS");
        }
    }

    @Test
    void isIdempotentOnSecondRun() throws SQLException {
        MigrationRunner runner = new MigrationRunner(new MigrationContext(dataSource, new H2Dialect()));
        runner.run(MIGRATION_PACKAGE);
        runner.run(MIGRATION_PACKAGE);

        assertThat(count("SELECT COUNT(*) FROM accounts")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM polydb_schema_history")).isEqualTo(2);
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
