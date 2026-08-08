package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deletes rows matching {@code where}, as a parameterised {@code DELETE}. An empty {@code where}
 * deletes every row. Not automatically reversible (the removed rows are not captured).
 */
public record DeleteDataOperation(String tableName, Map<String, Object> where) implements MigrationOperation {

    public DeleteDataOperation {
        where = new LinkedHashMap<>(where);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = "DELETE FROM " + tableName + DataSql.whereClause(where);
        return List.of(new SqlStatement(sql, DataSql.values(where)));
    }

    @Override
    public String describe() {
        return "Delete from " + tableName;
    }
}
