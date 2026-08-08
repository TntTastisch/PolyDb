package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.dialect.AbstractSqlDialect;
import de.tnttastisch.polydb.migration.operation.AddColumnOperation;
import de.tnttastisch.polydb.migration.operation.AddForeignKeyOperation;
import de.tnttastisch.polydb.migration.operation.AlterColumnOperation;
import de.tnttastisch.polydb.migration.operation.DropColumnOperation;
import de.tnttastisch.polydb.migration.operation.DropForeignKeyOperation;
import de.tnttastisch.polydb.migration.operation.DropIndexOperation;
import de.tnttastisch.polydb.migration.operation.DropTableOperation;
import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.operation.RawSqlOperation;
import de.tnttastisch.polydb.migration.operation.RenameColumnOperation;
import de.tnttastisch.polydb.migration.operation.RenameTableOperation;
import de.tnttastisch.polydb.migration.precondition.Precondition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fluent, dialect-agnostic API for describing a migration as a sequence of {@link MigrationOperation}s.
 * The developer states <em>what</em> should change (create a table, add a column, seed rows); PolyDB
 * decides <em>how</em> to realise it per dialect. Sub-builders returned by {@code addColumn},
 * {@code createIndex} and {@code createTable} are resolved lazily at {@link #build()} time, so a fluent
 * chain such as {@code addColumn(...).notNull()} is captured in full.
 */
public final class MigrationBuilder {

    private final List<Supplier<MigrationOperation>> operations = new ArrayList<>();
    private final List<Precondition> preconditions = new ArrayList<>();

    /** Registers guards; if any is unmet at run time the whole migration is skipped rather than failing. */
    public MigrationBuilder preconditions(Precondition... conditions) {
        Collections.addAll(preconditions, conditions);
        return this;
    }

    // ------------------------------------------------------------------ tables

    public MigrationBuilder createTable(String name, Consumer<TableBuilder> spec) {
        TableBuilder tb = new TableBuilder(name);
        spec.accept(tb);
        operations.add(tb::toCreateOperation);
        return this;
    }

    public MigrationBuilder dropTable(String name) {
        operations.add(() -> new DropTableOperation(name));
        return this;
    }

    public MigrationBuilder renameTable(String from, String to) {
        operations.add(() -> new RenameTableOperation(from, to));
        return this;
    }

    // ------------------------------------------------------------------ columns

    public ColumnBuilder addColumn(String table, String column, Class<?> type) {
        ColumnBuilder cb = new ColumnBuilder(column, type);
        operations.add(() -> new AddColumnOperation(table, cb.toFieldModel()));
        return cb;
    }

    public MigrationBuilder dropColumn(String table, String column) {
        operations.add(() -> new DropColumnOperation(table, column));
        return this;
    }

    public MigrationBuilder renameColumn(String table, String from, String to) {
        operations.add(() -> new RenameColumnOperation(table, from, to));
        return this;
    }

    /**
     * Changes a column's type/nullability. The previous state is not known here, so the resulting
     * operation is not automatically reversible; provide a manual {@code down()} to roll it back.
     */
    public ColumnBuilder alterColumn(String table, String column, Class<?> type) {
        ColumnBuilder cb = new ColumnBuilder(column, type);
        operations.add(() -> new AlterColumnOperation(table, null, cb.toFieldModel()));
        return cb;
    }

    // ------------------------------------------------------------------ indexes

    public IndexBuilder createIndex(String table, String... columns) {
        IndexBuilder ib = new IndexBuilder(table, columns);
        operations.add(ib::toOperation);
        return ib;
    }

    public MigrationBuilder dropIndex(String table, String indexName) {
        operations.add(() -> new DropIndexOperation(table, indexName));
        return this;
    }

    // ------------------------------------------------------------------ foreign keys

    /** Adds a foreign key with a deterministic {@code fk_<table>_<column>} constraint name. */
    public MigrationBuilder addForeignKey(String table, String column, String refTable, String refColumn) {
        String constraint = AbstractSqlDialect.foreignKeyConstraintName(table, column);
        return addForeignKey(table, constraint, column, refTable, refColumn);
    }

    public MigrationBuilder addForeignKey(String table, String constraint, String column, String refTable, String refColumn) {
        operations.add(() -> new AddForeignKeyOperation(table, constraint, column, refTable, refColumn));
        return this;
    }

    public MigrationBuilder dropForeignKey(String table, String constraint) {
        operations.add(() -> new DropForeignKeyOperation(table, constraint));
        return this;
    }

    // ------------------------------------------------------------------ data / seed

    public SeedBuilder seed(String table) {
        return new SeedBuilder(this, table);
    }

    // ------------------------------------------------------------------ raw SQL escape hatch

    public MigrationBuilder sql(String sql) {
        operations.add(() -> new RawSqlOperation(sql));
        return this;
    }

    public MigrationBuilder sql(String up, String down) {
        operations.add(() -> new RawSqlOperation(up, down));
        return this;
    }

    // ------------------------------------------------------------------ assembly

    /** Package-private hook used by sub-builders (e.g. {@link SeedBuilder}) to append an operation. */
    void addOperation(MigrationOperation op) {
        operations.add(() -> op);
    }

    /** Resolves every registered operation (including deferred sub-builders) into an immutable plan. */
    public MigrationPlan build() {
        List<MigrationOperation> resolved = new ArrayList<>();
        for (Supplier<MigrationOperation> supplier : operations) {
            resolved.add(supplier.get());
        }
        return new MigrationPlan(resolved, preconditions);
    }

    /**
     * Builds an ordered column-to-value map from alternating key/value pairs, for seed rows:
     * {@code row("id", 1, "name", "ADMIN")}.
     */
    public static Map<String, Object> row(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("row() requires an even number of key/value arguments");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
