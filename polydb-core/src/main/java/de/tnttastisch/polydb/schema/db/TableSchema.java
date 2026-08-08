package de.tnttastisch.polydb.schema.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of a single existing database table: its columns plus the set of columns that already
 * carry a foreign-key constraint. Built by {@link DatabaseSchemaReader} and held inside a
 * {@link DatabaseSchema}; the comparator uses it to skip columns/constraints that are already present.
 */
public class TableSchema {

    private final String name;
    /** Columns keyed by lower-cased name for case-insensitive look-up. */
    private final Map<String, ColumnSchema> columns = new HashMap<>();

    /**
     * Lower-cased names of columns that already have a foreign-key constraint in the database.
     */
    private final Set<String> foreignKeyColumns = new HashSet<>();

    /** Lower-cased names of indexes present on this table (used by migration preconditions). */
    private final Set<String> indexNames = new HashSet<>();

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

    /**
     * @return {@code true} if {@code columnName} already has a foreign-key constraint, so the
     *         comparator can avoid emitting a duplicate {@code ADD CONSTRAINT}.
     */
    public boolean hasForeignKeyOn(String columnName) {
        return columnName != null && foreignKeyColumns.contains(columnName.toLowerCase());
    }

    public Set<String> getIndexNames() {
        return indexNames;
    }

    public void addIndex(String indexName) {
        if (indexName != null) {
            indexNames.add(indexName.toLowerCase());
        }
    }

    /** @return {@code true} if an index named {@code indexName} exists on this table (case-insensitive). */
    public boolean hasIndex(String indexName) {
        return indexName != null && indexNames.contains(indexName.toLowerCase());
    }
}
