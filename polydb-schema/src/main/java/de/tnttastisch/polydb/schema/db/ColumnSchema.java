package de.tnttastisch.polydb.schema.db;

public class ColumnSchema {

    private final String name;
    private final int sqlType;
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
