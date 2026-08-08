package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialect for PostgreSQL. Uses standard double-quoted identifiers and supports foreign keys, both
 * inherited from {@link AbstractSqlDialect}.
 *
 * <p>Notable quirks: PostgreSQL encodes auto-increment in the <em>column type</em> rather than via a
 * trailing keyword. An auto-incrementing {@code int}/{@code long} becomes {@code SERIAL}/
 * {@code BIGSERIAL}, so {@link #getAutoIncrementKeyword()} returns an empty string. Native
 * {@code UUID} and {@code TIMESTAMPTZ} types are used, and the catch-all type is {@code TEXT}.
 */
public class PostgreSqlDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "PostgreSQL";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> field.isAutoIncrement() ? "SERIAL" : "INTEGER";
            case "long", "Long" -> field.isAutoIncrement() ? "BIGSERIAL" : "BIGINT";
            case "short", "Short" -> field.isAutoIncrement() ? "SMALLSERIAL" : "SMALLINT";
            case "byte", "Byte" -> "SMALLINT";
            case "boolean", "Boolean" -> "BOOLEAN";
            case "double", "Double" -> "DOUBLE PRECISION";
            case "float", "Float" -> "REAL";
            case "BigDecimal", "BigInteger" -> decimalType("NUMERIC", field);
            case "char", "Character" -> "CHAR(1)";
            case "LocalDateTime", "Timestamp", "Instant" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "LocalTime", "Time" -> "TIME";
            case "OffsetDateTime", "ZonedDateTime" -> "TIMESTAMPTZ";
            case "UUID" -> "UUID";
            case "byte[]" -> "BYTEA";
            default -> "TEXT";
        };
    }

    /**
     * No trailing keyword: auto-increment is conveyed through the {@code SERIAL}/{@code BIGSERIAL}
     * type chosen in {@link #getSqlType(FieldModel)}.
     */
    @Override
    protected String getAutoIncrementKeyword() {
        return "";
    }

    /**
     * PostgreSQL cannot change a column's type and its nullability in a single clause, so type and
     * the {@code SET}/{@code DROP NOT NULL} change are emitted as two comma-separated
     * {@code ALTER COLUMN} actions within one statement.
     */
    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " TYPE " + getSqlType(field) +
                (field.isNullable() ? ", ALTER COLUMN " + field.getColumnName() + " DROP NOT NULL" : ", ALTER COLUMN " + field.getColumnName() + " SET NOT NULL");
    }

    /** PostgreSQL uses {@code INSERT ... ON CONFLICT (keys) DO UPDATE SET ... = EXCLUDED....}. */
    @Override
    public String getUpsertSql(String tableName, List<String> columns, List<String> keyColumns) {
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String updates = columns.stream()
                .filter(c -> !keyColumns.contains(c))
                .map(c -> c + " = EXCLUDED." + c)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")" +
                " ON CONFLICT (" + String.join(", ", keyColumns) + ") DO UPDATE SET " + updates;
    }
}
