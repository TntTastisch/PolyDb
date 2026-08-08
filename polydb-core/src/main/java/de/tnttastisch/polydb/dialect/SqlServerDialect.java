package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

/**
 * Dialect for Microsoft SQL Server (T-SQL). Uses standard double-quoted identifiers and supports
 * foreign keys, both inherited from {@link AbstractSqlDialect}. (SQL Server also accepts the
 * bracket {@code [name]} form, but quoting falls back to the ANSI double quote.)
 *
 * <p>Notable quirks: strings use Unicode {@code NVARCHAR}, switching to {@code NVARCHAR(MAX)} once
 * the requested length exceeds the 4000-character page limit; {@code boolean} maps to {@code BIT};
 * auto-increment uses the {@code IDENTITY(1,1)} seed/increment syntax; the binary fallback is
 * {@code VARBINARY(MAX)}.
 */
public class SqlServerDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "Microsoft SQL Server";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "NVARCHAR(" + (field.getLength() > 4000 ? "MAX" : field.getLength()) + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "short", "Short" -> "SMALLINT";
            case "byte", "Byte" -> "TINYINT";
            case "boolean", "Boolean" -> "BIT";
            case "double", "Double" -> "FLOAT";
            case "float", "Float" -> "REAL";
            case "BigDecimal", "BigInteger" -> decimalType("DECIMAL", field);
            case "char", "Character" -> "NCHAR(1)";
            case "LocalDateTime", "Timestamp", "Instant" -> "DATETIME2";
            case "LocalDate" -> "DATE";
            case "LocalTime", "Time" -> "TIME";
            case "OffsetDateTime", "ZonedDateTime" -> "DATETIMEOFFSET";
            case "UUID" -> "UNIQUEIDENTIFIER";
            case "byte[]" -> "VARBINARY(MAX)";
            default -> "VARBINARY(MAX)";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "IDENTITY(1,1)";
    }

    /**
     * SQL Server uses {@code ALTER COLUMN} (not {@code MODIFY}); nullability is restated because an
     * {@code ALTER COLUMN} that omits it resets the column to nullable.
     */
    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "ALTER TABLE " + tableName + " ALTER COLUMN " + field.getColumnName() + " " + getSqlType(field) +
                (field.isNullable() ? " NULL" : " NOT NULL");
    }

    /**
     * SQL Server (2012+) uses the ANSI {@code OFFSET ... FETCH} form rather than {@code LIMIT}. It also
     * requires an {@code ORDER BY} to be present; the repository always supplies one (falling back to
     * the primary key) whenever it paginates, so the clause is safe to emit.
     */
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
