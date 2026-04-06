package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class MongoDialect implements Dialect {

    @Override
    public String getName() {
        return "MongoDB";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "string";
            case "int":
            case "Integer": return "int";
            case "long":
            case "Long": return "long";
            case "boolean":
            case "Boolean": return "bool";
            case "double":
            case "Double":
            case "float":
            case "Float": return "double";
            case "LocalDateTime":
            case "LocalDate": return "date";
            case "UUID": return "uuid";
            default: return "binData";
        }
    }

    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields) {
        return "db.createCollection('" + tableName + "')";
    }

    @Override
    public String getAddColumnSql(String tableName, FieldModel field) {
        return null;
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return null;
    }

    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return null;
    }

    @Override
    public String getCreateIndexSql(String tableName, IndexModel index) {
        StringBuilder columns = new StringBuilder("{");
        for (int i = 0; i < index.getColumns().size(); i++) {
            columns.append("'").append(index.getColumns().get(i)).append("': 1");
            if (i < index.getColumns().size() - 1) columns.append(", ");
        }
        columns.append("}");
        
        String options = index.isUnique() ? ", {unique: true}" : "";
        return "db." + tableName + ".createIndex(" + columns.toString() + options + ")";
    }

    @Override
    public String getDropIndexSql(String tableName, String indexName) {
        return "db." + tableName + ".dropIndex('" + indexName + "')";
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "db." + tableName + ".drop()";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "'" + identifier + "'";
    }
}
