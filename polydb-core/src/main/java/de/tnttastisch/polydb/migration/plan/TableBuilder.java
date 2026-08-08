package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.migration.operation.CreateTableOperation;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent description of a table's columns inside {@code createTable}, plus reusable helpers for
 * recurring column patterns (timestamps, soft delete, audit columns, UUID primary key).
 */
public final class TableBuilder {

    private final String tableName;
    private final List<ColumnBuilder> columns = new ArrayList<>();

    TableBuilder(String tableName) {
        this.tableName = tableName;
    }

    /** Declares a column of the given Java type; further constraints are set on the returned builder. */
    public ColumnBuilder column(String name, Class<?> type) {
        ColumnBuilder cb = new ColumnBuilder(name, type);
        columns.add(cb);
        return cb;
    }

    /** Convenience for a {@code VARCHAR(length)} column. */
    public ColumnBuilder string(String name, int length) {
        return column(name, String.class).length(length);
    }

    /** Convenience for a {@code UUID} primary-key column. */
    public ColumnBuilder uuidPrimaryKey(String name) {
        return column(name, UUID.class).primaryKey();
    }

    /** Adds {@code created_at} / {@code updated_at} timestamp columns defaulting to the current time. */
    public TableBuilder timestamps() {
        column("created_at", LocalDateTime.class).notNull().defaultValue("CURRENT_TIMESTAMP");
        column("updated_at", LocalDateTime.class).notNull().defaultValue("CURRENT_TIMESTAMP");
        return this;
    }

    /** Adds a nullable {@code deleted_at} column for soft-delete semantics. */
    public TableBuilder softDelete() {
        column("deleted_at", LocalDateTime.class).nullable();
        return this;
    }

    /** Adds {@code created_by} / {@code updated_by} audit columns. */
    public TableBuilder auditColumns() {
        column("created_by", String.class).length(100).nullable();
        column("updated_by", String.class).length(100).nullable();
        return this;
    }

    /** Marks the named, already-declared columns as the (composite) primary key. */
    public TableBuilder primaryKey(String... columnNames) {
        for (String col : columnNames) {
            for (ColumnBuilder cb : columns) {
                if (cb.columnName().equals(col)) {
                    cb.markPrimaryKey();
                }
            }
        }
        return this;
    }

    CreateTableOperation toCreateOperation() {
        List<FieldModel> fields = new ArrayList<>();
        for (ColumnBuilder cb : columns) {
            fields.add(cb.toFieldModel());
        }
        return new CreateTableOperation(tableName, fields);
    }
}
