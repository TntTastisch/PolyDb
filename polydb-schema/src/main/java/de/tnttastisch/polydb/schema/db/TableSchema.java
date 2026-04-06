package de.tnttastisch.polydb.schema.db;

import java.util.HashMap;
import java.util.Map;

public class TableSchema {

    private final String name;
    private final Map<String, ColumnSchema> columns = new HashMap<>();

    public TableSchema(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, ColumnSchema> getColumns() {
        return columns;
    }

    public void addColumn(ColumnSchema column) {
        columns.put(column.getName().toLowerCase(), column);
    }
}
