package de.tnttastisch.polydb.schema.model;

import java.util.ArrayList;
import java.util.List;

public class EntityModel {

    private final String className;
    private final String tableName;
    private final List<FieldModel> fields = new ArrayList<>();
    private final List<IndexModel> indexes = new ArrayList<>();

    public EntityModel(String className, String tableName) {
        this.className = className;
        this.tableName = tableName;
    }

    public String getClassName() {
        return className;
    }

    public String getTableName() {
        return tableName;
    }

    public List<FieldModel> getFields() {
        return fields;
    }

    public void addField(FieldModel field) {
        this.fields.add(field);
    }

    public List<IndexModel> getIndexes() {
        return indexes;
    }

    public void addIndex(IndexModel index) {
        this.indexes.add(index);
    }
}
