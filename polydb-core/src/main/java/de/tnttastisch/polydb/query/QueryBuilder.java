package de.tnttastisch.polydb.query;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for simple {@code SELECT} statements with equality predicates. It accumulates the
 * projection and {@code WHERE} conditions, then renders a parameterised SQL string whose values are
 * exposed separately via {@link #getParameters()} for binding by {@link QueryExecutor}.
 *
 * <p>Only equality predicates joined by {@code AND} are supported; there is no ordering, paging or
 * {@code OR} support. Column and table names are inlined verbatim and are <em>not</em> escaped or
 * validated, so they must come from trusted (schema) sources rather than untrusted input. The
 * builder is single-use and not thread-safe.</p>
 */
public class QueryBuilder {

    private final String tableName;
    /** Projected columns; an empty list renders as {@code SELECT *}. */
    private final List<String> selectColumns = new ArrayList<>();
    /** Rendered {@code column = ?} fragments, joined with {@code AND}. */
    private final List<String> whereClauses = new ArrayList<>();
    /** Bind values, kept in lock-step with the {@code ?} placeholders in {@link #whereClauses}. */
    private final List<Object> parameters = new ArrayList<>();

    public QueryBuilder(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Adds columns to the projection. Calls are additive, so invoking this repeatedly appends to the
     * existing selection; if no column is ever added the query falls back to {@code SELECT *}.
     *
     * @param columns column names to project
     * @return this builder, for chaining
     */
    public QueryBuilder select(String... columns) {
        for (String col : columns) {
            selectColumns.add(col);
        }
        return this;
    }

    /**
     * Adds an equality predicate. The value is not inlined into the SQL: a {@code ?} placeholder is
     * emitted and the value is queued in {@link #getParameters()}, so the order of {@code where}
     * calls must match the order in which parameters are later bound.
     *
     * @param column the column to compare
     * @param value  the value to match (may be {@code null}, though that binds as {@code = NULL}
     *               rather than {@code IS NULL})
     * @return this builder, for chaining
     */
    public QueryBuilder where(String column, Object value) {
        whereClauses.add(column + " = ?");
        parameters.add(value);
        return this;
    }

    /**
     * Renders the accumulated state into a SQL {@code SELECT} string. An empty projection becomes
     * {@code *} and the {@code WHERE} clause is omitted entirely when no predicate was added.
     *
     * @return the parameterised SQL; bind values are obtained from {@link #getParameters()}
     */
    public String buildSelect() {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectColumns));
        }
        sql.append(" FROM ").append(tableName);

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        return sql.toString();
    }

    /**
     * The bind values for the {@code ?} placeholders in the built SQL, in placeholder order. The
     * live backing list is returned, so it reflects any {@code where} calls made afterwards.
     *
     * @return the positional parameter values
     */
    public List<Object> getParameters() {
        return parameters;
    }
}
