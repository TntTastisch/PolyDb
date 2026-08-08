package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/**
 * Escape hatch for a raw SQL statement the fluent API cannot express. Reversible only when an explicit
 * {@code downSql} counterpart was supplied. Portability across dialects is the author's responsibility.
 */
public record RawSqlOperation(String sql, String downSql) implements MigrationOperation {

    public RawSqlOperation(String sql) {
        this(sql, null);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        return List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "SQL: " + (sql.length() > 60 ? sql.substring(0, 57) + "..." : sql);
    }

    @Override
    public boolean isReversible() {
        return downSql != null;
    }

    @Override
    public MigrationOperation reverse() {
        if (downSql == null) {
            throw new IrreversibleOperationException(describe());
        }
        return new RawSqlOperation(downSql, null);
    }
}
