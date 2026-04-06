package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MySqlDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "MySQL";
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
            case "Boolean": return "TINYINT(1)";
            case "double":
            case "Double": return "DOUBLE";
            case "float":
            case "Float": return "FLOAT";
            case "LocalDateTime": return "DATETIME";
            case "LocalDate": return "DATE";
            default: return "VARCHAR(255)";
        }
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "`" + identifier + "`";
    }
}
