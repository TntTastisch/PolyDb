package de.tnttastisch.polydb.query;

import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {

    private final String tableName;
    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> whereClauses = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();

    public QueryBuilder(String tableName) {
        this.tableName = tableName;
    }

    public QueryBuilder select(String... columns) {
        for (String col : columns) {
            selectColumns.add(col);
        }
        return this;
    }

    public QueryBuilder where(String column, Object value) {
        whereClauses.add(column + " = ?");
        parameters.add(value);
        return this;
    }

    public String buildSelect() {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectColumns));
        }
        sql.append(" FROM ").append(tableName);

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        return sql.toString();
    }

    public List<Object> getParameters() {
        return parameters;
    }
}
