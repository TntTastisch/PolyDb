package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inserts one row of seed data as a parameterised {@code INSERT}. The column-to-value map preserves
 * insertion order. Its inverse deletes the same row by exact column match (best-effort — intended for
 * seed rows with concrete key values).
 */
public record InsertDataOperation(String tableName, Map<String, Object> values) implements MigrationOperation {

    public InsertDataOperation {
        values = new LinkedHashMap<>(values);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = "INSERT INTO " + tableName + " (" + DataSql.columnList(values) + ") VALUES (" +
                DataSql.placeholders(values.size()) + ")";
        return List.of(new SqlStatement(sql, DataSql.values(values)));
    }

    @Override
    public String describe() {
        return "Insert into " + tableName;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new DeleteDataOperation(tableName, values);
    }
}
