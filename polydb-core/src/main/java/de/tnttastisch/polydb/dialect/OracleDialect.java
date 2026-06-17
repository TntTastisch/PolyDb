package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class OracleDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Oracle";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR2(" + field.getLength() + ")";
            case "int", "Integer" -> "NUMBER(10)";
            case "long", "Long" -> "NUMBER(19)";
            case "boolean", "Boolean" -> "NUMBER(1)";
            case "double", "Double" -> "FLOAT(126)";
            case "float", "Float" -> "FLOAT(63)";
            case "LocalDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "UUID" -> "RAW(16)";
            case "OffsetDateTime" -> "TIMESTAMP WITH TIME ZONE";
            default -> "BLOB";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "GENERATED AS IDENTITY";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " MODIFY " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? " NULL" : " NOT NULL");
    }

    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }
}
