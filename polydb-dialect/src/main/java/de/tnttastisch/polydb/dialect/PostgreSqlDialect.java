package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class PostgreSqlDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "PostgreSQL";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "VARCHAR(" + field.getLength() + ")";
            case "int":
            case "Integer": return field.isAutoIncrement() ? "SERIAL" : "INTEGER";
            case "long":
            case "Long": return field.isAutoIncrement() ? "BIGSERIAL" : "BIGINT";
            case "boolean":
            case "Boolean": return "BOOLEAN";
            case "double":
            case "Double": return "DOUBLE PRECISION";
            case "float":
            case "Float": return "REAL";
            case "LocalDateTime": return "TIMESTAMP";
            case "LocalDate": return "DATE";
            case "UUID": return "UUID";
            default: return "TEXT";
        }
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
