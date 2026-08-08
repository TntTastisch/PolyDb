package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.List;

/**
 * Adds a column described by a {@link FieldModel} to an existing table. Its inverse is a
 * {@link DropColumnOperation} that carries this column's definition so it can be restored.
 */
public record AddColumnOperation(String tableName, FieldModel field) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getAddColumnSql(tableName, field);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Add column " + field.getColumnName() + " to " + tableName;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new DropColumnOperation(tableName, field.getColumnName(), field);
    }
}
