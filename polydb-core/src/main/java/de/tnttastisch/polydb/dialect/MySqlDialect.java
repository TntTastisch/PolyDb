package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialect for MySQL. Serves as the base for the {@link MariaDbDialect}, which only diverges on a
 * handful of type mappings.
 *
 * <p>Notable quirks: identifiers are quoted with backticks rather than the standard double quote;
 * tables are created with {@code ENGINE=InnoDB} so that foreign keys are actually enforced (the
 * legacy MyISAM engine silently ignores them); {@code boolean} maps to {@code TINYINT(1)} since
 * MySQL has no dedicated boolean type, and time-zone-aware timestamps fall back to {@code DATETIME(6)}.
 */
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
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String" -> "VARCHAR(" + field.getLength() + ")";
            case "int", "Integer" -> "INT";
            case "long", "Long" -> "BIGINT";
            case "short", "Short" -> "SMALLINT";
            case "byte", "Byte" -> "TINYINT";
            case "boolean", "Boolean" -> "TINYINT(1)";
            case "double", "Double" -> "DOUBLE";
            case "float", "Float" -> "FLOAT";
            case "BigDecimal", "BigInteger" -> decimalType("DECIMAL", field);
            case "char", "Character" -> "CHAR(1)";
            case "LocalDateTime", "Timestamp", "Instant" -> "DATETIME";
            case "OffsetDateTime", "ZonedDateTime" -> "DATETIME(6)";
            case "LocalDate" -> "DATE";
            case "LocalTime", "Time" -> "TIME";
            case "byte[]" -> "BLOB";
            default -> "VARCHAR(255)";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTO_INCREMENT";
    }

    /**
     * MySQL quotes identifiers with backticks instead of the SQL-standard double quote.
     */
    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "`" + identifier + "`";
    }

    /** MySQL implicitly commits each DDL statement, so a migration cannot be rolled back as a unit. */
    @Override
    public boolean supportsTransactionalDdl() {
        return false;
    }

    /** MySQL/MariaDB spell foreign-key removal as {@code DROP FOREIGN KEY}, not {@code DROP CONSTRAINT}. */
    @Override
    public String getDropForeignKeySql(String tableName, String constraintName) {
        return "ALTER TABLE " + tableName + " DROP FOREIGN KEY " + constraintName;
    }

    /** MySQL uses {@code INSERT ... ON DUPLICATE KEY UPDATE}; {@code VALUES(col)} avoids extra parameters. */
    @Override
    public String getUpsertSql(String tableName, List<String> columns, List<String> keyColumns) {
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String updates = columns.stream()
                .filter(c -> !keyColumns.contains(c))
                .map(c -> c + " = VALUES(" + c + ")")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")" +
                " ON DUPLICATE KEY UPDATE " + updates;
    }
}
