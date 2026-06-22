package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;

public class MySqlDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "MySQL";
    }

    /**
     * Foreign keys require the InnoDB storage engine (the modern default), so we declare it
     * explicitly to be safe.
     */
    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields, List<RelationModel> relations) {
        return super.getCreateTableSql(tableName, fields, relations) + " ENGINE=InnoDB";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "TINYINT(1)";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "FLOAT";
            case "LocalDateTime", "Timestamp" -> "DATETIME";
            case "OffsetDateTime" -> "DATETIME(6)";
            case "LocalDate" -> "DATE";
            default -> "VARCHAR(255)";
        };
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
