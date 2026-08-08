package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Fluent, mutable description of one column, materialised into a {@link FieldModel} when its owning
 * table or {@code addColumn}/{@code alterColumn} operation is built. Sensible defaults (nullable,
 * length 255) match the entity-annotation defaults.
 */
public final class ColumnBuilder {

    private final String name;
    private final Class<?> type;
    private boolean id = false;
    private boolean autoIncrement = false;
    private boolean nullable = true;
    private boolean unique = false;
    private int length = 255;
    private int precision = 0;
    private int scale = 0;
    private String defaultValue = "";

    ColumnBuilder(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    public ColumnBuilder notNull() {
        this.nullable = false;
        return this;
    }

    public ColumnBuilder nullable() {
        this.nullable = true;
        return this;
    }

    public ColumnBuilder unique() {
        this.unique = true;
        return this;
    }

    public ColumnBuilder primaryKey() {
        this.id = true;
        this.nullable = false;
        return this;
    }

    public ColumnBuilder autoIncrement() {
        this.autoIncrement = true;
        return this;
    }

    public ColumnBuilder length(int length) {
        this.length = length;
        return this;
    }

    public ColumnBuilder precision(int precision) {
        this.precision = precision;
        return this;
    }

    public ColumnBuilder scale(int scale) {
        this.scale = scale;
        return this;
    }

    /** Sets the raw SQL {@code DEFAULT} literal (emitted verbatim), e.g. {@code "0"} or {@code "'active'"}. */
    public ColumnBuilder defaultValue(String sqlLiteral) {
        this.defaultValue = sqlLiteral;
        return this;
    }

    String columnName() {
        return name;
    }

    void markPrimaryKey() {
        this.id = true;
        this.nullable = false;
    }

    FieldModel toFieldModel() {
        return new FieldModel(null, name, type, id, autoIncrement, nullable, unique, length, precision, scale, defaultValue);
    }
}
