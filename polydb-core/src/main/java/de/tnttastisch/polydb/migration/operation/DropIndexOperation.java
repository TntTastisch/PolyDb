package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.util.List;

/**
 * Drops an index by name. Reversible only when the original {@code restore} definition was captured
 * (as when this is the reverse of a create); otherwise it is irreversible.
 */
public record DropIndexOperation(String tableName, String indexName, IndexModel restore) implements MigrationOperation {

    public DropIndexOperation(String tableName, String indexName) {
        this(tableName, indexName, null);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getDropIndexSql(tableName, indexName);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Drop index " + indexName + " on " + tableName;
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
        return new CreateIndexOperation(tableName, restore);
    }
}
