package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;

public class SqliteDialect extends AbstractSqlDialect {

    @Override
    public String getName() {
        return "SQLite";
    }

    @Override
    public String getSqlType(FieldModel field) {
        String typeName = field.getType().getSimpleName();
        return switch (typeName) {
            case "String", "UUID", "OffsetDateTime", "LocalDateTime", "Timestamp", "LocalDate" -> "TEXT";
            case "int", "Integer", "long", "Long", "boolean", "Boolean" -> "INTEGER";
            case "double", "Double", "float", "Float" -> "REAL";
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
