package de.tnttastisch.polydb.migration;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.H2Dialect;
import de.tnttastisch.polydb.migration.executor.ExecutionResult;
import de.tnttastisch.polydb.migration.executor.MigrationExecutor;
import de.tnttastisch.polydb.migration.plan.ExecutionMode;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static de.tnttastisch.polydb.migration.plan.MigrationBuilder.row;
import static de.tnttastisch.polydb.migration.precondition.Preconditions.ifTableExists;
import static de.tnttastisch.polydb.migration.precondition.Preconditions.ifTableMissing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests of the shared {@link MigrationExecutor} against in-memory H2: applying builder-built
 * plans (DDL + seed data), dry-run / preview producing SQL without writes, precondition skipping,
 * transactional rollback, and best-effort compensation for non-transactional dialects.
 */
class MigrationExecutorTest {

    private Connection conn;
    private final MigrationExecutor executor = new MigrationExecutor(new H2Dialect());

    @BeforeEach
    void openConnection() throws SQLException {
        String url = "jdbc:h2:mem:mig_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        conn = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void closeConnection() throws SQLException {
        conn.close();
    }

    @Test
    void createsTableWithHelperColumnsAndSeedsData() throws SQLException {
        MigrationBuilder m = new MigrationBuilder();
        m.createTable("roles", t -> {
            t.column("id", Integer.class).primaryKey();
            t.string("name", 50).notNull();
            t.timestamps();
        });
        m.seed("roles")
                .insert(row("id", 1, "name", "ADMIN"))
                .insert(row("id", 2, "name", "USER"))
                .upsert("id", row("id", 2, "name", "MEMBER"));

        ExecutionResult result = executor.run(m.build(), conn, ExecutionMode.EXECUTE);

        assertThat(result.isApplied()).isTrue();
        assertThat(tableExists("ROLES")).isTrue();
        assertThat(hasColumn("ROLES", "CREATED_AT")).isTrue();
        assertThat(count("SELECT COUNT(*) FROM roles")).isEqualTo(2);
        assertThat(scalar("SELECT name FROM roles WHERE id = 2")).isEqualTo("MEMBER");
    }

    @Test
    void updateAndDeleteSeedData() throws SQLException {
        MigrationBuilder create = new MigrationBuilder();
        create.createTable("widgets", t -> {
            t.column("id", Integer.class).primaryKey();
            t.string("label", 50).nullable();
        });
        create.seed("widgets")
                .insert(row("id", 1, "label", "a"))
                .insert(row("id", 2, "label", "b"));
        executor.run(create.build(), conn, ExecutionMode.EXECUTE);

        MigrationBuilder mutate = new MigrationBuilder();
        mutate.seed("widgets")
                .update(row("label", "updated"), row("id", 1))
                .delete(row("id", 2));
        executor.run(mutate.build(), conn, ExecutionMode.EXECUTE);

        assertThat(scalar("SELECT label FROM widgets WHERE id = 1")).isEqualTo("updated");
        assertThat(count("SELECT COUNT(*) FROM widgets")).isEqualTo(1);
    }

    @Test
    void dryRunRendersSqlWithoutApplying() throws SQLException {
        MigrationBuilder m = new MigrationBuilder();
        m.createTable("dry_table", t -> t.column("id", Integer.class).primaryKey());

        ExecutionResult result = executor.run(m.build(), conn, ExecutionMode.DRY_RUN);

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getSql()).contains("CREATE TABLE dry_table");
        assertThat(tableExists("DRY_TABLE")).isFalse();
    }

    @Test
    void previewRendersParameterisedSeedSql() throws SQLException {
        MigrationBuilder create = new MigrationBuilder();
        create.createTable("preview_t", t -> {
            t.column("id", Integer.class).primaryKey();
            t.string("name", 50);
        });
        executor.run(create.build(), conn, ExecutionMode.EXECUTE);

        MigrationBuilder m = new MigrationBuilder();
        m.seed("preview_t").insert(row("id", 1, "name", "x"));
        ExecutionResult result = executor.run(m.build(), conn, ExecutionMode.PREVIEW);

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getSql()).contains("INSERT INTO preview_t");
        assertThat(count("SELECT COUNT(*) FROM preview_t")).isEqualTo(0);
    }

    @Test
    void skipsMigrationWhenPreconditionNotMet() throws SQLException {
        MigrationBuilder m = new MigrationBuilder();
        m.preconditions(ifTableExists("does_not_exist"));
        m.createTable("guarded", t -> t.column("id", Integer.class).primaryKey());

        ExecutionResult result = executor.run(m.build(), conn, ExecutionMode.EXECUTE);

        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getSkipReason()).contains("does_not_exist");
        assertThat(tableExists("GUARDED")).isFalse();
    }

    @Test
    void runsMigrationWhenPreconditionMet() throws SQLException {
        MigrationBuilder m = new MigrationBuilder();
        m.preconditions(ifTableMissing("brand_new"));
        m.createTable("brand_new", t -> t.column("id", Integer.class).primaryKey());

        ExecutionResult result = executor.run(m.build(), conn, ExecutionMode.EXECUTE);

        assertThat(result.isApplied()).isTrue();
        assertThat(tableExists("BRAND_NEW")).isTrue();
    }

    @Test
    void rollsBackDataChangesOnFailureWhenTransactional() throws SQLException {
        // Create (and commit) the table first; H2 commits DDL implicitly, so this is durable.
        MigrationBuilder create = new MigrationBuilder();
        create.createTable("tx_data", t -> t.column("id", Integer.class).primaryKey());
        executor.run(create.build(), conn, ExecutionMode.EXECUTE);

        // A dialect that reports transactional DDL makes the executor wrap the plan in one transaction;
        // H2 honours rollback for the DML, so the inserted row is undone when the next statement fails.
        MigrationExecutor transactional = new MigrationExecutor(new TransactionalDialect());
        MigrationBuilder m = new MigrationBuilder();
        m.seed("tx_data").insert(row("id", 1));
        m.sql("INSERT INTO a_table_that_does_not_exist (id) VALUES (2)");

        assertThatThrownBy(() -> transactional.run(m.build(), conn, ExecutionMode.EXECUTE))
                .isInstanceOf(PolyDBException.class);

        assertThat(count("SELECT COUNT(*) FROM tx_data")).isEqualTo(0);
    }

    @Test
    void compensatesAppliedOperationsWhenDialectIsNotTransactional() throws SQLException {
        // H2 reports no transactional DDL, so the executor applies operations one by one and, on
        // failure, reverses the ones already applied (the CREATE TABLE's reverse is a DROP TABLE).
        MigrationBuilder m = new MigrationBuilder();
        m.createTable("comp_first", t -> t.column("id", Integer.class).primaryKey());
        m.sql("INSERT INTO another_missing_table (id) VALUES (1)");

        assertThatThrownBy(() -> executor.run(m.build(), conn, ExecutionMode.EXECUTE))
                .isInstanceOf(PolyDBException.class);

        assertThat(tableExists("COMP_FIRST")).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    /** An H2 dialect forced to report transactional DDL, to exercise the transactional rollback path. */
    static class TransactionalDialect extends H2Dialect {
        @Override
        public boolean supportsTransactionalDdl() {
            return true;
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private int count(String sql) throws SQLException {
        return scalarInt(sql);
    }

    private int scalarInt(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
