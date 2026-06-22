package de.tnttastisch.polydb.schema.db;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory representation of the live database structure (the set of existing tables and their
 * columns/foreign keys), populated by {@link DatabaseSchemaReader}. The
 * {@link de.tnttastisch.polydb.schema.comparison.SchemaComparator} consults it to tell which entity
 * tables and columns are missing and which already exist.
 */
public class DatabaseSchema {

    /** Tables keyed by lower-cased name so look-ups are case-insensitive across dialects. */
    private final Map<String, TableSchema> tables = new HashMap<>();

    public Map<String, TableSchema> getTables() {
        return tables;
    }

    public void addTable(TableSchema table) {
        tables.put(table.getName().toLowerCase(), table);
    }

    /**
     * @return the table matching {@code name} case-insensitively, or {@code null} if it does not
     *         exist in the database (signalling the comparator that it must be created).
     */
    public TableSchema getTable(String name) {
        return tables.get(name.toLowerCase());
    }
}
