package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractSqlDialect implements Dialect {

    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (\n");

        List<String> definitions = new ArrayList<>();
        for (FieldModel field : fields) {
            StringBuilder def = new StringBuilder("  ");
            def.append(field.getColumnName()).append(" ").append(getSqlType(field));
            if (!field.isNullable()) {
                def.append(" NOT NULL");
            }
            if (field.isAutoIncrement()) {
                def.append(" ").append(getAutoIncrementKeyword());
            }
            if (field.isUnique() && !field.isId()) {
                def.append(" UNIQUE");
            }
            definitions.add(def.toString());
        }

        List<String> pkColumns = fields.stream()
                .filter(FieldModel::isId)
                .map(FieldModel::getColumnName)
                .collect(Collectors.toList());

        if (!pkColumns.isEmpty()) {
            definitions.add("  PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        sql.append(String.join(",\n", definitions));
        sql.append("\n)");
        return sql.toString();
    }

    protected abstract String getAutoIncrementKeyword();

    @Override
    public String getAddColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ADD " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? "" : " NOT NULL");
    }

    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }

    @Override
    public String getCreateIndexSql(String tableName, IndexModel index) {
        String unique = index.isUnique() ? "UNIQUE " : "";
        String indexName = index.getName().isEmpty() ? "idx_" + tableName + "_" + String.join("_", index.getColumns()) : index.getName();
        return "CREATE " + unique + "INDEX " + indexName + " ON " + tableName + " (" + String.join(", ", index.getColumns()) + ")";
    }

    @Override
    public String getDropIndexSql(String tableName, String indexName) {
        return "DROP INDEX " + indexName;
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "DROP TABLE " + tableName;
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " MODIFY " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? "" : " NOT NULL");
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier + "\"";
    }
}
