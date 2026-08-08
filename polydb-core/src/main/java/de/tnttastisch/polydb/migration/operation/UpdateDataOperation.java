package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Updates rows matching {@code where} with the assignments in {@code set}, as a parameterised
 * {@code UPDATE}. An empty {@code where} updates every row. Not automatically reversible (the previous
 * values are not captured), so a manual {@code down()} is required to roll it back.
 */
public record UpdateDataOperation(String tableName, Map<String, Object> set,
                                  Map<String, Object> where) implements MigrationOperation {

    public UpdateDataOperation {
        set = new LinkedHashMap<>(set);
        where = new LinkedHashMap<>(where);
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = "UPDATE " + tableName + " SET " + DataSql.assignments(set, ", ") + DataSql.whereClause(where);
        List<Object> params = new ArrayList<>(DataSql.values(set));
        params.addAll(DataSql.values(where));
        return List.of(new SqlStatement(sql, params));
    }

    @Override
    public String describe() {
        return "Update " + tableName;
    }
}
