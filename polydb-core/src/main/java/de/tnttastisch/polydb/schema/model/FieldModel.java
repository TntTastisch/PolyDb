package de.tnttastisch.polydb.schema.model;

import java.lang.reflect.Field;

public class FieldModel {

    private final Field field;
    private final String columnName;
    private final Class<?> type;
    private final boolean id;
    private final boolean autoIncrement;
    private final boolean nullable;
    private final boolean unique;
    private final int length;
    private final int precision;
    private final int scale;

    public FieldModel(Field field, String columnName, Class<?> type, boolean id, boolean autoIncrement, boolean nullable, boolean unique, int length, int precision, int scale) {
        this.field = field;
        this.columnName = columnName;
        this.type = type;
        this.id = id;
        this.autoIncrement = autoIncrement;
        this.nullable = nullable;
        this.unique = unique;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
    }

    public Field getField() {
        return field;
    }

    public String getColumnName() {
        return columnName;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isId() {
        return id;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isUnique() {
        return unique;
    }

    public int getLength() {
        return length;
    }

    public int getPrecision() {
        return precision;
    }

    public int getScale() {
        return scale;
    }
}
