package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inserts a row or updates it when a row with the same {@code keyColumns} already exists. The concrete
 * SQL (H2 {@code MERGE}, PostgreSQL {@code ON CONFLICT}, MySQL {@code ON DUPLICATE KEY}) is chosen by
 * the {@link Dialect}; the operation always binds one parameter per column, in insertion order. Not
 * automatically reversible.
 */
public record UpsertDataOperation(String tableName, List<String> keyColumns,
                                  Map<String, Object> values) implements MigrationOperation {

    public UpsertDataOperation {
        keyColumns = List.copyOf(keyColumns);
        values = new LinkedHashMap<>(values);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = dialect.getUpsertSql(tableName, columns, keyColumns);
        return List.of(new SqlStatement(sql, DataSql.values(values)));
    }

    @Override
    public String describe() {
        return "Upsert into " + tableName;
    }
}
