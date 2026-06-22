package de.tnttastisch.polydb.schema.db;

/**
 * Immutable snapshot of one column as it currently exists in the live database, read from JDBC
 * {@link java.sql.DatabaseMetaData}. This is the "actual state" counterpart to a
 * {@link de.tnttastisch.polydb.schema.model.FieldModel} ("desired state"); the
 * {@link de.tnttastisch.polydb.schema.comparison.SchemaComparator} diffs the two to decide what DDL
 * to emit.
 */
public class ColumnSchema {

    private final String name;
    /** JDBC {@link java.sql.Types} code reported by the driver (e.g. {@code VARCHAR}, {@code BIGINT}). */
    private final int sqlType;
    /** Driver-specific type name as stored in the catalog (e.g. {@code "int8"}, {@code "varchar"}). */
    private final String typeName;
    private final int size;
    private final boolean nullable;
    private final boolean autoIncrement;

    public ColumnSchema(String name, int sqlType, String typeName, int size, boolean nullable, boolean autoIncrement) {
        this.name = name;
        this.sqlType = sqlType;
        this.typeName = typeName;
        this.size = size;
        this.nullable = nullable;
        this.autoIncrement = autoIncrement;
    }

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getSize() {
        return size;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }
}
