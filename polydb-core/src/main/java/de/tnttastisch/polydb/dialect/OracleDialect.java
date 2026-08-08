package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Dialect for Oracle Database. Uses standard double-quoted identifiers and supports foreign keys,
 * both inherited from {@link AbstractSqlDialect}.
 *
 * <p>Notable quirks: Oracle has no plain {@code INTEGER}/{@code BOOLEAN} types, so numeric and
 * boolean values map onto {@code NUMBER} with an explicit precision ({@code NUMBER(1)} stands in for
 * boolean); strings use {@code VARCHAR2} rather than {@code VARCHAR}; {@code UUID} is stored as a
 * 16-byte {@code RAW(16)}. Auto-increment uses identity columns ({@code GENERATED AS IDENTITY},
 * available since Oracle 12c).
 */
public class OracleDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Oracle";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR2(" + field.getLength() + ")";
            case "int", "Integer" -> "NUMBER(10)";
            case "long", "Long" -> "NUMBER(19)";
            case "short", "Short" -> "NUMBER(5)";
            case "byte", "Byte" -> "NUMBER(3)";
            case "boolean", "Boolean" -> "NUMBER(1)";
            case "double", "Double" -> "FLOAT(126)";
            case "float", "Float" -> "FLOAT(63)";
            case "BigDecimal", "BigInteger" -> decimalType("NUMBER", field);
            case "char", "Character" -> "CHAR(1)";
            // Oracle has no standalone TIME type; a time-of-day maps onto TIMESTAMP.
            case "LocalDateTime", "Timestamp", "Instant", "LocalTime", "Time" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "UUID" -> "RAW(16)";
            case "OffsetDateTime", "ZonedDateTime" -> "TIMESTAMP WITH TIME ZONE";
            case "byte[]" -> "BLOB";
            default -> "BLOB";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "GENERATED AS IDENTITY";
    }

    /**
     * Oracle uses {@code MODIFY} (like the base dialect) but spells out the nullability explicitly:
     * an unqualified {@code MODIFY} keeps the existing nullability, so {@code NULL}/{@code NOT NULL}
     * is always appended.
     */
    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " MODIFY " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? " NULL" : " NOT NULL");
    }

    // Spelled out explicitly though it matches the default; Oracle requires the DROP COLUMN form.
    @Override
    public String getDropColumnSql(String tableName, String columnName) {
        return "ALTER TABLE " + tableName + " DROP COLUMN " + columnName;
    }

    /** Oracle implicitly commits each DDL statement, so a migration cannot be rolled back as a unit. */
    @Override
    public boolean supportsTransactionalDdl() {
        return false;
    }
}
