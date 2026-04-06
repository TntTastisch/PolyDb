package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Db2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "IBM DB2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "VARCHAR(" + field.getLength() + ")";
            case "int":
            case "Integer": return "INTEGER";
            case "long":
            case "Long": return "BIGINT";
            case "boolean":
            case "Boolean": return "SMALLINT";
            case "double":
            case "Double": return "DOUBLE";
            case "float":
            case "Float": return "REAL";
            case "LocalDateTime": return "TIMESTAMP";
            case "LocalDate": return "DATE";
            default: return "BLOB";
        }
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
