package de.tnttastisch.polydb.query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin JDBC helper that runs parameterised SQL against a {@link DataSource}. It owns connection and
 * statement lifecycle (every call borrows a connection, prepares a statement and closes both via
 * try-with-resources) so callers only supply SQL, positional parameters and, for reads, a mapper.
 *
 * <p>Each call uses its own connection; the executor does not span statements in a transaction, so
 * a sequence of updates is not atomic with respect to one another. Checked {@link SQLException}s are
 * wrapped in unchecked {@link RuntimeException}s so the calling code is not forced to handle them.</p>
 */
public class QueryExecutor {

    private final DataSource dataSource;

    public QueryExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs a {@code SELECT} (or any query producing a result set) and maps every returned row.
     *
     * <p>Parameters are bound positionally: element {@code i} of {@code params} is bound to JDBC
     * placeholder {@code i + 1} (placeholders are 1-based). Values are bound with
     * {@link PreparedStatement#setObject}, leaving type handling to the driver. The mapper is invoked
     * once per row with the cursor already advanced onto it.</p>
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // JDBC placeholders are 1-based, so shift the 0-based list index by one.
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query execution failed", e);
        }
        return results;
    }

    /**
     * Runs an {@code INSERT}, {@code UPDATE} or {@code DELETE} with the given positional parameters,
     * bound the same way as {@link #executeQuery}.
     *
     * @param sql    the statement, using {@code ?} placeholders
     * @param params positional parameter values, in placeholder order
     * @return the number of affected rows as reported by {@link PreparedStatement#executeUpdate()}
     * @throws RuntimeException if execution fails (wraps the {@link SQLException})
     */
    public int executeUpdate(String sql, List<Object> params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Same 1-based positional binding as executeQuery.
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update execution failed", e);
        }
    }
}
