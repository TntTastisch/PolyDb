package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class SqlServerDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Microsoft SQL Server";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "NVARCHAR(" + (field.getLength() > 4000 ? "MAX" : field.getLength()) + ")";
            case "int":
            case "Integer": return "INT";
            case "long":
            case "Long": return "BIGINT";
            case "boolean":
            case "Boolean": return "BIT";
            case "double":
            case "Double": return "FLOAT";
            case "float":
            case "Float": return "REAL";
            case "LocalDateTime": return "DATETIME2";
            case "LocalDate": return "DATE";
            case "UUID": return "UNIQUEIDENTIFIER";
            default: return "VARBINARY(MAX)";
        }
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
