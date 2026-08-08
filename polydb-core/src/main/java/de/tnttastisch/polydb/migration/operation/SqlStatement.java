package de.tnttastisch.polydb.migration.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single executable statement produced by a {@link MigrationOperation}: the SQL text plus the
 * positional bind parameters for its {@code ?} placeholders. DDL operations yield parameter-less
 * statements; data (seed) operations yield parameterised ones so values are bound safely rather than
 * inlined. The migration engine executes each statement (as a {@code PreparedStatement} when
 * parameterised) and can render it for SQL preview without touching the database.
 */
public final class SqlStatement {

    private final String sql;
    private final List<Object> parameters;

    /** A statement with no bind parameters (typical for DDL). */
    public SqlStatement(String sql) {
        this(sql, Collections.emptyList());
    }

    /**
     * A statement whose {@code ?} placeholders are bound, in order, to {@code parameters}. The list is
     * copied defensively; {@code null} values are permitted (they bind as SQL {@code NULL}).
     */
    public SqlStatement(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public String getSql() {
        return sql;
    }

    /** The bind values for the {@code ?} placeholders, in placeholder order; never {@code null}. */
    public List<Object> getParameters() {
        return parameters;
    }

    public boolean isParameterized() {
        return !parameters.isEmpty();
    }

    @Override
    public String toString() {
        return sql;
    }
}
