package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.migration.operation.DeleteDataOperation;
import de.tnttastisch.polydb.migration.operation.InsertDataOperation;
import de.tnttastisch.polydb.migration.operation.UpdateDataOperation;
import de.tnttastisch.polydb.migration.operation.UpsertDataOperation;

import java.util.List;
import java.util.Map;

/**
 * Declarative seed-data API for a single table, obtained via {@code MigrationBuilder.seed(table)}. Each
 * call appends a parameterised data operation to the parent migration. Build value maps with
 * {@link MigrationBuilder#row(Object...)}.
 */
public final class SeedBuilder {

    private final MigrationBuilder parent;
    private final String tableName;

    SeedBuilder(MigrationBuilder parent, String tableName) {
        this.parent = parent;
        this.tableName = tableName;
    }

    public SeedBuilder insert(Map<String, Object> values) {
        parent.addOperation(new InsertDataOperation(tableName, values));
        return this;
    }

    public SeedBuilder update(Map<String, Object> set, Map<String, Object> where) {
        parent.addOperation(new UpdateDataOperation(tableName, set, where));
        return this;
    }

    public SeedBuilder delete(Map<String, Object> where) {
        parent.addOperation(new DeleteDataOperation(tableName, where));
        return this;
    }

    public SeedBuilder upsert(String keyColumn, Map<String, Object> values) {
        return upsert(List.of(keyColumn), values);
    }

    public SeedBuilder upsert(List<String> keyColumns, Map<String, Object> values) {
        parent.addOperation(new UpsertDataOperation(tableName, keyColumns, values));
        return this;
    }
}
