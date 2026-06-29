package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class SqliteDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "SQLite";
    }

    @Override
    public String getSqlType(FieldModel field) {
        // Enums are stored by name in a string column.
        String typeName = field.getType().isEnum() ? "String" : field.getType().getSimpleName();
        return switch (typeName) {
            case "String", "UUID", "OffsetDateTime", "ZonedDateTime", "LocalDateTime", "Timestamp",
                 "Instant", "LocalDate", "LocalTime", "Time", "char", "Character" -> "TEXT";
            case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
                 "boolean", "Boolean" -> "INTEGER";
            case "double", "Double", "float", "Float" -> "REAL";
            case "BigDecimal", "BigInteger" -> "NUMERIC";
            default -> "BLOB";
        };
    }

    @Override
    protected String getAutoIncrementKeyword() {
        return "AUTOINCREMENT";
    }

    @Override
    public String getModifyColumnSql(String tableName, FieldModel field) {
        return "-- SQLite does not support MODIFY COLUMN for " + field.getColumnName();
    }

    /**
     * SQLite cannot add a foreign key to an existing table; foreign keys are only declared inline at
     * {@code CREATE TABLE} time.
     */
    @Override
    public boolean supportsAddForeignKeyViaAlter() {
        return false;
    }

    /**
     * Foreign-key enforcement is off by default in SQLite and must be enabled per connection.
     */
    @Override
    public String getEnableForeignKeysStatement() {
        return "PRAGMA foreign_keys = ON";
    }
}
