package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/** Renames a table. Symmetrically reversible by swapping the two names. */
public record RenameTableOperation(String fromName, String toName) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        return List.of(new SqlStatement(dialect.getRenameTableSql(fromName, toName)));
    }

    @Override
    public String describe() {
        return "Rename table " + fromName + " to " + toName;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new RenameTableOperation(toName, fromName);
    }
}
