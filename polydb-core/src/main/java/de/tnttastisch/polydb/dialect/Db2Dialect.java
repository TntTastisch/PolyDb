package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Dialect for IBM DB2. Identifiers use the standard double-quote form inherited from
 * {@link AbstractSqlDialect}, and foreign keys are fully supported.
 *
 * <p>Notable quirks: DB2 has no native boolean type before recent releases, so {@code boolean}
 * maps to {@code SMALLINT}; auto-increment columns use the SQL-standard
 * {@code GENERATED ALWAYS AS IDENTITY} clause rather than a vendor keyword.
 */
public class Db2Dialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "IBM DB2";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INTEGER";
            case "long", "Long" -> "BIGINT";
            case "short", "Short", "byte", "Byte" -> "SMALLINT";
            case "boolean", "Boolean" -> "SMALLINT";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "REAL";
            case "BigDecimal", "BigInteger" -> decimalType("DECIMAL", field);
            case "char", "Character" -> "CHAR(1)";
            case "OffsetDateTime", "ZonedDateTime" -> "TIMESTAMP WITH TIME ZONE";
            case "LocalDateTime", "Timestamp", "Instant" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "LocalTime", "Time" -> "TIME";
            case "byte[]" -> "BLOB";
            default -> "BLOB";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "GENERATED ALWAYS AS IDENTITY";
    }

    /**
     * DB2 spells column-type changes as {@code ALTER COLUMN ... SET DATA TYPE} rather than the
     * default {@code MODIFY} form.
     */
    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " SET DATA TYPE " + getSqlType(field);
    }

    /** DB2 pages with the ANSI {@code OFFSET ... FETCH} form rather than {@code LIMIT}. */
    @Override
    public String getLimitClause(Long limit, Long offset) {
        if (limit == null && offset == null) {
            return "";
        }
        long skip = offset == null ? 0 : offset;
        StringBuilder clause = new StringBuilder("OFFSET ").append(skip).append(" ROWS");
        if (limit != null) {
            clause.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
        }
        return clause.toString();
    }
}
