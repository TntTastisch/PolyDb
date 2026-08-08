package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.List;

/**
 * Drops a column. Reversible only when the column's prior {@code restore} definition was captured
 * (as when this is the reverse of an add); otherwise it is irreversible.
 */
public record DropColumnOperation(String tableName, String columnName, FieldModel restore) implements MigrationOperation {

    public DropColumnOperation(String tableName, String columnName) {
        this(tableName, columnName, null);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getDropColumnSql(tableName, columnName);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Drop column " + columnName + " from " + tableName;
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
        return new AddColumnOperation(tableName, restore);
    }
}
