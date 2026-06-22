package de.tnttastisch.polydb.schema.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, dialect-independent description of a single persistent entity. The
 * {@link de.tnttastisch.polydb.schema.parser.EntityParser} produces one {@code EntityModel} per
 * {@code @Entity} class; downstream the model feeds DDL generation and schema comparison, so it is
 * the central hand-off between the reflection phase and the SQL phase of the schema pipeline.
 *
 * <p>It groups three orthogonal facets of an entity: scalar/foreign-key {@link FieldModel columns},
 * declared {@link IndexModel indexes}, and {@link RelationModel relations}. Foreign-key columns
 * appear in <em>both</em> {@link #fields} (as a scalar column to emit) and {@link #relations} (as the
 * association metadata), so consumers must reconcile the two views.</p>
 */
public class EntityModel {

    /** Fully-qualified Java class name, used as the stable key when correlating entities by type. */
    private final String className;
    private final String tableName;
    private final List<FieldModel> fields = new ArrayList<>();
    private final List<IndexModel> indexes = new ArrayList<>();
    private final List<RelationModel> relations = new ArrayList<>();

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

    public List<RelationModel> getRelations() {
        return relations;
    }

    public void addRelation(RelationModel relation) {
        this.relations.add(relation);
    }
}
