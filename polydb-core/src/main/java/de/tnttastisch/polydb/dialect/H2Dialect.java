package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class H2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "H2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "BOOLEAN";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "FLOAT";
            case "LocalDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "UUID" -> "UUID";
            case "OffsetDateTime" -> "TIMESTAMP WITH TIME ZONE";
            default -> "VARCHAR(255)";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? "" : " NOT NULL");
    }
}
