package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CassandraDialect implements Dialect {

    @Override
    public String getName() {
        return "Cassandra (CQL)";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        switch (typeName) {
            case "String": return "text";
            case "int":
            case "Integer": return "int";
            case "long":
            case "Long": return "bigint";
            case "boolean":
            case "Boolean": return "boolean";
            case "double":
            case "Double": return "double";
            case "float":
            case "Float": return "float";
            case "LocalDateTime": return "timestamp";
            case "LocalDate": return "date";
            case "UUID": return "uuid";
            default: return "blob";
        }
    }

    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (\n");

        for (int i = 0; i < fields.size(); i++) {
            FieldModel field = fields.get(i);
            sql.append("  ").append(field.getColumnName()).append(" ").append(getSqlType(field));
            if (i < fields.size() - 1 || fields.stream().anyMatch(FieldModel::isId)) {
                sql.append(",\n");
            }
        }

        List<String> pkColumns = fields.stream()
                .filter(FieldModel::isId)
                .map(FieldModel::getColumnName)
                .collect(java.util.stream.Collectors.toList());

        if (!pkColumns.isEmpty()) {
            sql.append("  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
        }

        sql.append("\n)");
        return sql.toString();
    }

    @Override
    public String getAddColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ADD " + field.getColumnName() + " " + getSqlType(field);
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return null;
    }

    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP " + columnName;
    }

    @Override
    public String getCreateIndexSql(String tableName, IndexModel index) {
        String indexName = index.getName().isEmpty() ? "idx_" + tableName + "_" + String.join("_", index.getColumns()) : index.getName();
        return "CREATE INDEX " + indexName + " ON " + tableName + " (" + String.join(", ", index.getColumns()) + ")";
    }

    @Override
    public String getDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "drop table " + tableName;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier + "\"";
    }
}
