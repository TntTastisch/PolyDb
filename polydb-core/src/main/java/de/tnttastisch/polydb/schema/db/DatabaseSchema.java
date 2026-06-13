package de.tnttastisch.polydb.schema.db;

import java.util.HashMap;
import java.util.Map;

public class DatabaseSchema {

    private final Map<String, TableSchema> tables = new HashMap<>();

    public Map<String, TableSchema> getTables() {
        return tables;
    }

    public void addTable(TableSchema table) {
        tables.put(table.getName().toLowerCase(), table);
    }

    public TableSchema getTable(String name) {
        return tables.get(name.toLowerCase());
    }
}
