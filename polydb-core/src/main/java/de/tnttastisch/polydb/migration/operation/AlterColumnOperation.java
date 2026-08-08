package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.List;

/**
 * Changes a column's definition from {@code from} to {@code to} (type and/or nullability). Because it
 * carries both the previous and the desired state it is symmetrically reversible: its inverse alters
 * the column back to {@code from}.
 */
public record AlterColumnOperation(String tableName, FieldModel from, FieldModel to) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getModifyColumnSql(tableName, to);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Alter column " + to.getColumnName() + " on " + tableName;
    }

    @Override
    public boolean isReversible() {
        return from != null;
    }

    @Override
    public MigrationOperation reverse() {
        if (from == null) {
            throw new IrreversibleOperationException(describe());
        }
        return new AlterColumnOperation(tableName, to, from);
    }
}
