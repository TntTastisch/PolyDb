package de.tnttastisch.polydb.migration.executor;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.operation.SqlStatement;
import de.tnttastisch.polydb.migration.plan.ExecutionMode;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;
import de.tnttastisch.polydb.migration.precondition.Precondition;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchemaReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The single execution path for every schema or data change in PolyDB. It takes a {@link MigrationPlan}
 * — produced identically by automatic migration and by manual {@code MigrationBuilder}s — evaluates its
 * preconditions, renders each operation to SQL via the {@link Dialect}, and applies it.
 *
 * <p>Execution mode decides the effect: {@link ExecutionMode#EXECUTE} applies the plan, while
 * {@link ExecutionMode#DRY_RUN}/{@link ExecutionMode#PREVIEW} only render SQL and make no changes.
 * Atomicity follows the dialect's capability: when {@link Dialect#supportsTransactionalDdl()} the whole
 * plan runs in one transaction (rolled back on failure); otherwise operations are applied one by one and
 * failure triggers a best-effort compensation that reverses the operations already applied.</p>
 */
public final class MigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);

    private final Dialect dialect;
    private final DatabaseSchemaReader schemaReader = new DatabaseSchemaReader();

    public MigrationExecutor(Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Processes {@code plan} on {@code connection} in the given {@code mode}.
     *
     * @return the outcome (applied, skipped by precondition, or rendered-only)
     * @throws PolyDBException if execution fails (after rolling back / compensating where possible)
     */
    public ExecutionResult run(MigrationPlan plan, Connection connection, ExecutionMode mode) {
        String skip = firstUnmetPrecondition(plan, connection);
        if (skip != null) {
            log.info("Skipping migration ({})", skip);
            return ExecutionResult.skipped(skip);
        }

        List<SqlStatement> statements = render(plan.getOperations());
        SqlScript script = new SqlScript(statements);

        if (mode != ExecutionMode.EXECUTE) {
            return ExecutionResult.rendered(mode, plan.getOperations(), script);
        }

        if (dialect.supportsTransactionalDdl()) {
            executeTransactional(connection, statements);
        } else {
            executeWithCompensation(connection, plan.getOperations());
        }
        return ExecutionResult.executed(plan.getOperations(), script);
    }

    /** Renders a bare list of operations to a preview script without any database access. */
    public SqlScript preview(List<MigrationOperation> operations) {
        return new SqlScript(render(operations));
    }

    private String firstUnmetPrecondition(MigrationPlan plan, Connection connection) {
        if (plan.getPreconditions().isEmpty()) {
            return null;
        }
        DatabaseSchema schema = schemaReader.readSchema(connection);
        for (Precondition precondition : plan.getPreconditions()) {
            if (!precondition.isMet(schema)) {
                return "precondition not met: " + precondition.describe();
            }
        }
        return null;
    }

    private List<SqlStatement> render(List<MigrationOperation> operations) {
        List<SqlStatement> statements = new ArrayList<>();
        for (MigrationOperation operation : operations) {
            statements.addAll(operation.toStatements(dialect));
        }
        return statements;
    }

    private void executeTransactional(Connection conn, List<SqlStatement> statements) {
        boolean previousAutoCommit;
        try {
            previousAutoCommit = conn.getAutoCommit();
        } catch (SQLException e) {
            throw new PolyDBException("Failed to read auto-commit state", e);
        }
        try {
            conn.setAutoCommit(false);
            for (SqlStatement statement : statements) {
                executeStatement(conn, statement);
            }
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new PolyDBException("Migration failed and was rolled back: " + e.getMessage(), e);
        } finally {
            restoreAutoCommit(conn, previousAutoCommit);
        }
    }

    private void executeWithCompensation(Connection conn, List<MigrationOperation> operations) {
        List<MigrationOperation> applied = new ArrayList<>();
        try {
            for (MigrationOperation operation : operations) {
                for (SqlStatement statement : operation.toStatements(dialect)) {
                    executeStatement(conn, statement);
                }
                applied.add(operation);
            }
        } catch (SQLException e) {
            compensate(conn, applied);
            throw new PolyDBException("Migration failed; compensating changes were attempted: " + e.getMessage(), e);
        }
    }

    /** Reverses the already-applied operations in opposite order; stops at the first irreversible one. */
    private void compensate(Connection conn, List<MigrationOperation> applied) {
        for (int i = applied.size() - 1; i >= 0; i--) {
            MigrationOperation operation = applied.get(i);
            if (!operation.isReversible()) {
                log.error("Cannot compensate irreversible operation '{}'; database may be left in a partial state",
                        operation.describe());
                return;
            }
            try {
                for (SqlStatement statement : operation.reverse().toStatements(dialect)) {
                    executeStatement(conn, statement);
                }
            } catch (SQLException e) {
                log.error("Compensation failed for '{}': {}", operation.describe(), e.getMessage());
                return;
            }
        }
    }

    private void executeStatement(Connection conn, SqlStatement statement) throws SQLException {
        log.debug("Executing: {}", statement.getSql());
        if (!statement.isParameterized()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement.getSql());
            }
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(statement.getSql())) {
            List<Object> params = statement.getParameters();
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            log.error("Rollback failed: {}", e.getMessage());
        }
    }

    private void restoreAutoCommit(Connection conn, boolean previous) {
        try {
            conn.setAutoCommit(previous);
        } catch (SQLException e) {
            log.warn("Failed to restore auto-commit: {}", e.getMessage());
        }
    }
}
