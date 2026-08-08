package de.tnttastisch.polydb.migration.plan;

import de.tnttastisch.polydb.migration.operation.CreateIndexOperation;
import de.tnttastisch.polydb.schema.model.IndexModel;

import java.util.Arrays;
import java.util.List;

/** Fluent description of an index inside {@code createIndex}; defaults to a non-unique, auto-named index. */
public final class IndexBuilder {

    private final String tableName;
    private final List<String> columns;
    private boolean unique = false;
    private String name = "";

    IndexBuilder(String tableName, String... columns) {
        this.tableName = tableName;
        this.columns = Arrays.asList(columns);
    }

    public IndexBuilder unique() {
        this.unique = true;
        return this;
    }

    /** Sets an explicit index name (otherwise a deterministic {@code idx_<table>_<cols>} is derived). */
    public IndexBuilder name(String name) {
        this.name = name;
        return this;
    }

    CreateIndexOperation toOperation() {
        return new CreateIndexOperation(tableName, new IndexModel(name, columns, unique));
    }
}
