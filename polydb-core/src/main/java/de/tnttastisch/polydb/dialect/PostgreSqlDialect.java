package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class PostgreSqlDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "PostgreSQL";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> field.isAutoIncrement() ? "SERIAL" : "INTEGER";
            case "long", "Long" -> field.isAutoIncrement() ? "BIGSERIAL" : "BIGINT";
            case "boolean", "Boolean" -> "BOOLEAN";
            case "double", "Double" -> "DOUBLE PRECISION";
            case "float", "Float" -> "REAL";
            case "LocalDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "OffsetDateTime" -> "TIMESTAMPTZ";
            case "UUID" -> "UUID";
            default -> "TEXT";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " TYPE " + getSqlType(field) +
                (field.isNullable() ? ", ALTER COLUMN " + field.getColumnName() + " DROP NOT NULL" : ", ALTER COLUMN " + field.getColumnName() + " SET NOT NULL");
    }
}
