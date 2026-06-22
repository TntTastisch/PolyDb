package de.tnttastisch.polydb.schema.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TableSchema {

    private final String name;
    private final Map<String, ColumnSchema> columns = new HashMap<>();

    /**
     * Lower-cased names of columns that already have a foreign-key constraint in the database.
     */
    private final Set<String> foreignKeyColumns = new HashSet<>();

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

    public Set<String> getForeignKeyColumns() {
        return foreignKeyColumns;
    }

    public void addForeignKeyColumn(String columnName) {
        if (columnName != null) {
            foreignKeyColumns.add(columnName.toLowerCase());
        }
    }

    public boolean hasForeignKeyOn(String columnName) {
        return columnName != null && foreignKeyColumns.contains(columnName.toLowerCase());
    }
}
