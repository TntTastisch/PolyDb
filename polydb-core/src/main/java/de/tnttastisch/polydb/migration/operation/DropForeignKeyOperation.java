package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/**
 * Drops a named foreign-key constraint. Reversible only when the original {@code restore} definition
 * was captured (as when this is the reverse of an add); otherwise it is irreversible.
 */
public record DropForeignKeyOperation(String tableName, String constraintName,
                                      AddForeignKeyOperation restore) implements MigrationOperation {

    public DropForeignKeyOperation(String tableName, String constraintName) {
        this(tableName, constraintName, null);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        if (!dialect.supportsForeignKeys()) {
            return List.of();
        }
        String sql = dialect.getDropForeignKeySql(tableName, constraintName);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Drop foreign key " + constraintName + " on " + tableName;
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
