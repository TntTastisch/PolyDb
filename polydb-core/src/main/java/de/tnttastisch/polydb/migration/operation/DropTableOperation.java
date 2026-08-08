package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/**
 * Drops a table. Reversible only when a {@code restore} {@link CreateTableOperation} was captured (as
 * happens when this drop is itself the reverse of a create); otherwise the prior structure is unknown
 * and the operation is irreversible, so a manual {@code down()} is required.
 */
public record DropTableOperation(String tableName, CreateTableOperation restore) implements MigrationOperation {

    public DropTableOperation(String tableName) {
        this(tableName, null);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getDropTableSql(tableName);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Drop table " + tableName;
    }

    @Override
    public boolean isReversible() {
        return restore != null;
    }

    @Override
    public MigrationOperation reverse() {
        if (restore == null) {
            throw new IrreversibleOperationException(describe());
        }
        return restore;
    }
}
