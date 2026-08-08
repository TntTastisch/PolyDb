package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.List;

/**
 * Adds a foreign-key constraint to an existing table via {@code ALTER TABLE}. Yields no statement for
 * dialects without foreign keys, or those (SQLite) that cannot add them via {@code ALTER} — matching
 * the previous schema-generator behaviour. Its inverse is a {@link DropForeignKeyOperation}.
 */
public record AddForeignKeyOperation(String tableName, String constraintName, String column,
                                     String referencedTable, String referencedColumn) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        if (!dialect.supportsForeignKeys() || !dialect.supportsAddForeignKeyViaAlter()) {
            return List.of();
        }
        String sql = dialect.getAddForeignKeySql(tableName, constraintName, column, referencedTable, referencedColumn);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Add foreign key " + tableName + "." + column + " -> " + referencedTable + "." + referencedColumn;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new DropForeignKeyOperation(tableName, constraintName, this);
    }
}
