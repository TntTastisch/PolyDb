package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/** Renames a column. Symmetrically reversible by swapping the two names. */
public record RenameColumnOperation(String tableName, String fromName, String toName) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        return List.of(new SqlStatement(dialect.getRenameColumnSql(tableName, fromName, toName)));
    }

    @Override
    public String describe() {
        return "Rename column " + fromName + " to " + toName + " on " + tableName;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new RenameColumnOperation(tableName, toName, fromName);
    }
}
