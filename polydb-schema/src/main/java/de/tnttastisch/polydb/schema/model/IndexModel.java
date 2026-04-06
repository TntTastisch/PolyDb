package de.tnttastisch.polydb.schema.model;

import java.util.List;

public class IndexModel {

    private final String name;
    private final List<String> columns;
    private final boolean unique;

    public IndexModel(String name, List<String> columns, boolean unique) {
        this.name = name;
        this.columns = columns;
        this.unique = unique;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isUnique() {
        return unique;
    }
}
