package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class SqlServerDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Microsoft SQL Server";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "NVARCHAR(" + (field.getLength() > 4000 ? "MAX" : field.getLength()) + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "BIT";
            case "double", "Double" -> "FLOAT";
            case "float", "Float" -> "REAL";
            case "LocalDateTime", "Timestamp" -> "DATETIME2";
            case "LocalDate" -> "DATE";
            case "OffsetDateTime" -> "DATETIMEOFFSET";
            case "UUID" -> "UNIQUEIDENTIFIER";
            default -> "VARBINARY(MAX)";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "IDENTITY(1,1)";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? " NULL" : " NOT NULL");
    }
}
