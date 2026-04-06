package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class H2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "H2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "VARCHAR(" + field.getLength() + ")";
            case "int":
            case "Integer": return "INT";
            case "long":
            case "Long": return "BIGINT";
            case "boolean":
            case "Boolean": return "BOOLEAN";
            case "double":
            case "Double": return "DOUBLE";
            case "float":
            case "Float": return "FLOAT";
            case "LocalDateTime": return "TIMESTAMP";
            case "LocalDate": return "DATE";
            case "UUID": return "UUID";
            default: return "VARCHAR(255)";
        }
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
