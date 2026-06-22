package de.tnttastisch.polydb.schema.model;

import java.lang.reflect.Field;

/**
 * Immutable description of one database column derived from an entity field. Produced by the
 * {@link de.tnttastisch.polydb.schema.parser.EntityParser} and consumed by the dialect to emit
 * column DDL and by the repository to bind/read values.
 *
 * <p>Most instances map a scalar field directly to a column. A foreign-key column is a special case:
 * see {@link #relation} — it carries association metadata and its persisted value comes from the
 * referenced entity rather than from reading the field as-is.</p>
 */
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

    /**
     * When non-null, this column is the foreign-key column of an owning relation. Its
     * {@link #field} is the association field (e.g. {@code User author}) while {@link #type} is the
     * referenced entity's id type, so the dialect picks the correct SQL type. Persistence of this
     * column is handled through the relation, not by reading the association field directly.
     */
    private RelationModel relation;

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

    /**
     * The owning relation this column belongs to, or {@code null} for a plain scalar column.
     */
    public RelationModel getRelation() {
        return relation;
    }

    public void setRelation(RelationModel relation) {
        this.relation = relation;
    }

    /**
     * @return {@code true} if this column is the foreign-key column of an owning relation.
     */
    public boolean isForeignKey() {
        return relation != null;
    }
}
