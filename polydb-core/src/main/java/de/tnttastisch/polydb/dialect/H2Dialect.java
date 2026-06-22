package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Dialect for the H2 in-memory/embedded database, commonly used for tests. Uses standard
 * double-quoted identifiers and supports foreign keys, both inherited from
 * {@link AbstractSqlDialect}.
 *
 * <p>Notable quirks: H2 has a native {@code UUID} type and accepts MySQL's {@code AUTO_INCREMENT}
 * keyword (in addition to its own identity syntax), which keeps generated DDL portable with the
 * MySQL family. The fallback type for unknown classes is {@code VARCHAR(255)}.
 */
public class H2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "H2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "boolean", "Boolean" -> "BOOLEAN";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "FLOAT";
            case "LocalDateTime", "Timestamp" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "UUID" -> "UUID";
            case "OffsetDateTime" -> "TIMESTAMP WITH TIME ZONE";
            default -> "VARCHAR(255)";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTO_INCREMENT";
    }

    /**
     * H2 changes a column's type with {@code ALTER COLUMN <name> <type>} (no {@code MODIFY} or
     * {@code SET DATA TYPE} keyword), so the default implementation is overridden.
     */
    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? "" : " NOT NULL");
    }
}
