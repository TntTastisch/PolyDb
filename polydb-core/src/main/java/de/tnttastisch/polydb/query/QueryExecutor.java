package de.tnttastisch.polydb.query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin JDBC helper that runs parameterised SQL against a {@link DataSource}. When a
 * {@link TransactionTemplate} is active for the data source on the current thread, every call runs on
 * that transaction's shared connection and leaves it open; otherwise each call borrows a fresh
 * connection and closes it (auto-commit). Either way the caller only supplies SQL, positional
 * parameters and, for reads, a mapper.
 *
 * <p>Parameters are bound positionally with {@link PreparedStatement#setObject}. Checked
 * {@link SQLException}s are wrapped in unchecked {@link RuntimeException}s.</p>
 */
public class QueryExecutor {

    private final DataSource dataSource;

    public QueryExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs a {@code SELECT} (or any query producing a result set) and maps every returned row.
     *
     * @param sql    the query, using {@code ?} placeholders
     * @param params positional parameter values, in placeholder order
     * @param mapper maps each result-set row to a {@code T}
     * @param <T>    the mapped row type
     * @return the mapped rows in result-set order; empty when nothing matched
     * @throws RuntimeException if the query fails or a row cannot be mapped (wraps the {@link SQLException})
     */
    public <T> List<T> executeQuery(String sql, List<Object> params, ResultMapper<T> mapper) {
        List<T> results = new ArrayList<>();
        Connection connection = TransactionResources.boundConnection(dataSource);
        boolean managed = connection != null;
        try {
            if (!managed) {
                connection = dataSource.getConnection();
            }
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                bind(pstmt, params);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapper.map(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query execution failed", e);
        } finally {
            closeIfUnmanaged(connection, managed);
        }
        return results;
    }

    /**
     * Runs an {@code INSERT}, {@code UPDATE} or {@code DELETE} with the given positional parameters.
     *
     * @param sql    the statement, using {@code ?} placeholders
     * @param params positional parameter values, in placeholder order
     * @return the number of affected rows as reported by {@link PreparedStatement#executeUpdate()}
     * @throws RuntimeException if execution fails (wraps the {@link SQLException})
     */
    public int executeUpdate(String sql, List<Object> params) {
        Connection connection = TransactionResources.boundConnection(dataSource);
        boolean managed = connection != null;
        try {
            if (!managed) {
                connection = dataSource.getConnection();
            }
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                bind(pstmt, params);
                return pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Update execution failed", e);
        } finally {
            closeIfUnmanaged(connection, managed);
        }
    }

    /** Binds positional parameters: element {@code i} maps to the 1-based placeholder {@code i + 1}. */
    private static void bind(PreparedStatement pstmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            pstmt.setObject(i + 1, params.get(i));
        }
    }

    /** Closes a connection only when it was borrowed for this call (never a transaction's connection). */
    private static void closeIfUnmanaged(Connection connection, boolean managed) {
        if (connection != null && !managed) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best-effort close of a per-call connection
            }
        }
    }
}
