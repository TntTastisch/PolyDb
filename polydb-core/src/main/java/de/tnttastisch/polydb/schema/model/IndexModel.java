package de.tnttastisch.polydb.schema.model;

import java.util.List;

/**
 * Immutable description of a database index requested via the {@code @Index} annotation (either
 * class-level, spanning several columns, or field-level on a single column). Collected on the
 * {@link EntityModel} during parsing and translated to {@code CREATE INDEX} DDL by the dialect.
 */
public class IndexModel {

    /** Index name; may be empty when the dialect is expected to derive one. */
    private final String name;
    /** Columns covered by the index, in order; a composite index lists more than one. */
    private final List<String> columns;
    /** Whether the index enforces uniqueness across {@link #columns}. */
    private final boolean unique;

    public IndexModel(String name, List<String> columns, boolean unique) {
        this.name = name;
        this.columns = columns;
        this.unique = unique;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isUnique() {
        return unique;
    }
}
