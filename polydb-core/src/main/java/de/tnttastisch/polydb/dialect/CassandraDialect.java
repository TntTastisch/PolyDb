package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;

/**
 * Cassandra is query-first and has no joins or enforced foreign keys. Relations are modelled through
 * <em>denormalisation</em>, user-defined types (UDTs) or collection columns; referential integrity
 * is not enforced.
 *
 * <p>This dialect emits CQL rather than SQL: it implements {@link Dialect} directly instead of
 * extending {@link AbstractSqlDialect}, because Cassandra lacks foreign keys, has no
 * {@code MODIFY COLUMN}, and uses its own lowercase type names ({@code text}, {@code uuid},
 * {@code blob}, …). All foreign-key methods are therefore no-ops.
 */
public class CassandraDialect implements Dialect {

    @Override
    public String getName() {
        return "Cassandra (CQL)";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a text column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "text";
            case "int", "Integer" -> "int";
            case "long", "Long" -> "bigint";
            case "short", "Short" -> "smallint";
            case "byte", "Byte" -> "tinyint";
            case "boolean", "Boolean" -> "boolean";
            case "double", "Double" -> "double";
            case "float", "Float" -> "float";
            case "BigDecimal" -> "decimal";
            case "BigInteger" -> "varint";
            case "char", "Character" -> "text";
            case "Timestamp", "LocalDateTime", "OffsetDateTime", "ZonedDateTime", "Instant" -> "TIMESTAMP";
            case "LocalDate" -> "date";
            case "LocalTime", "Time" -> "time";
            case "UUID" -> "uuid";
            case "byte[]" -> "blob";
            default -> "blob";
        };
    }

    /**
     * Builds a CQL {@code CREATE TABLE}. The {@code relations} argument is ignored: Cassandra has no
     * foreign keys, so only column definitions and the primary key are emitted.
     */
    @Override
    public String getCreateTableSql(String tableName, List<FieldModel> fields, List<RelationModel> relations) {
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

    /**
     * Cassandra does not allow changing a column's type in place, so there is no migration
     * statement to emit.
     */
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

    // ------------------------------------------------------------------ foreign keys (no-op)

    @Override
    public boolean supportsForeignKeys() {
        return false;
    }

    @Override
    public boolean supportsAddForeignKeyViaAlter() {
        return false;
    }

    @Override
    public String getForeignKeyDefinition(String constraintName, String column, String refTable, String refColumn) {
        return null;
    }

    @Override
    public String getAddForeignKeySql(String tableName, String constraintName, String column, String refTable, String refColumn) {
        return null;
    }

    @Override
    public String getEnableForeignKeysStatement() {
        return null;
    }
}
