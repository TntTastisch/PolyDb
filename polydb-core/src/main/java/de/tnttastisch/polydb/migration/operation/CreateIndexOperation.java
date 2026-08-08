package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.util.List;

/**
 * Creates an index on a table. Its inverse drops the index by its effective name (the declared name,
 * or a deterministic {@code idx_<table>_<cols>} when none was given).
 */
public record CreateIndexOperation(String tableName, IndexModel index) implements MigrationOperation {

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getCreateIndexSql(tableName, index);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Create " + (index.isUnique() ? "unique index" : "index") + " on " + tableName +
                " (" + String.join(", ", index.getColumns()) + ")";
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new DropIndexOperation(tableName, effectiveIndexName(tableName, index), index);
    }

    /** The index name the dialect would use: the declared name, or a derived {@code idx_<table>_<cols>}. */
    static String effectiveIndexName(String tableName, IndexModel index) {
        if (index.getName() == null || index.getName().isEmpty()) {
            return "idx_" + tableName + "_" + String.join("_", index.getColumns());
        }
        return index.getName();
    }
}
