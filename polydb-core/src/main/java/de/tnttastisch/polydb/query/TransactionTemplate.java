package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.core.exception.PolyDBException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs a unit of work inside a single database transaction: it opens a connection, turns off
 * auto-commit, binds the connection for the current thread (so every repository call on that thread
 * joins the same transaction), commits on success and rolls back if the action throws. Build one from
 * a data source — typically {@code new TransactionTemplate(polyDB.getDataSource())}.
 *
 * <pre>{@code
 * TransactionTemplate tx = new TransactionTemplate(polyDB.getDataSource());
 * tx.executeWithoutResult(() -> {
 *     accounts.save(from);   // both saves commit together,
 *     accounts.save(to);     // or neither does
 * });
 * }</pre>
 *
 * <p>Nesting is join semantics: a template invoked while a transaction is already active for its data
 * source simply runs the action in that outer transaction (no new transaction, no intermediate commit).
 * A read-only variant hints the driver via {@link Connection#setReadOnly(boolean)}.</p>
 */
public final class TransactionTemplate {

    private final DataSource dataSource;

    public TransactionTemplate(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Runs {@code action} in a transaction and returns its result.
     *
     * @param action the work to run
     * @param <T>    the result type
     * @return the action's result
     */
    public <T> T execute(Supplier<T> action) {
        return run(action, false);
    }

    /** Runs {@code action} in a transaction with no result. */
    public void executeWithoutResult(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /** Runs {@code action} in a read-only transaction (a hint to the driver) and returns its result. */
    public <T> T executeReadOnly(Supplier<T> action) {
        return run(action, true);
    }

    private <T> T run(Supplier<T> action, boolean readOnly) {
        if (TransactionResources.isActive(dataSource)) {
            // Already inside a transaction for this data source: join it rather than nest.
            return action.get();
        }

        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new PolyDBException("Could not open a transaction", e);
        }

        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            if (readOnly) {
                connection.setReadOnly(true);
            }
            TransactionResources.bind(dataSource, connection);

            T result = action.get();

            connection.commit();
            committed = true;
            return result;
        } catch (SQLException e) {
            throw new PolyDBException("Transaction failed", e);
        } finally {
            TransactionResources.unbind(dataSource);
            if (!committed) {
                rollbackQuietly(connection);
            }
            restoreAndClose(connection, readOnly);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // best-effort rollback; the original failure is what propagates
        }
    }

    private static void restoreAndClose(Connection connection, boolean readOnly) {
        try {
            connection.setAutoCommit(true);
            if (readOnly) {
                connection.setReadOnly(false);
            }
        } catch (SQLException ignored) {
            // pooled connections are reset on return anyway
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing useful to do if close fails
        }
    }
}
