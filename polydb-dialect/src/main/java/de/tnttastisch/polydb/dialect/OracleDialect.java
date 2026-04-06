package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class OracleDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Oracle";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "VARCHAR2(" + field.getLength() + ")";
            case "int":
            case "Integer": return "NUMBER(10)";
            case "long":
            case "Long": return "NUMBER(19)";
            case "boolean":
            case "Boolean": return "NUMBER(1)";
            case "double":
            case "Double": return "FLOAT(126)";
            case "float":
            case "Float": return "FLOAT(63)";
            case "LocalDateTime": return "TIMESTAMP";
            case "LocalDate": return "DATE";
            case "UUID": return "RAW(16)";
            default: return "BLOB";
        }
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
