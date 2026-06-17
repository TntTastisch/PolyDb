package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class Db2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "IBM DB2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INTEGER";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "SMALLINT";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "REAL";
            case "OffsetDateTime" -> "TIMESTAMP WITH TIME ZONE";
            case "LocalDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            default -> "BLOB";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "GENERATED ALWAYS AS IDENTITY";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " SET DATA TYPE " + getSqlType(field);
    }
}
