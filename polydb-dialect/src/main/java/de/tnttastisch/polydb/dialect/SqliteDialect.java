package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class SqliteDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "SQLite";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "TEXT";
            case "int":
            case "Integer":
            case "long":
            case "Long": return "INTEGER";
            case "boolean":
            case "Boolean": return "INTEGER";
            case "double":
            case "Double":
            case "float":
            case "Float": return "REAL";
            case "LocalDateTime":
            case "LocalDate": return "TEXT";
            case "UUID": return "TEXT";
            default: return "BLOB";
        }
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTOINCREMENT";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "-- SQLite does not support MODIFY COLUMN for " + field.getColumnName();
    }
}
