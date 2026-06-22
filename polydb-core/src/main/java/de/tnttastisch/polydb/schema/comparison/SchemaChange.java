package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.Collections;
import java.util.List;

public abstract class SchemaChange {

    public static class CreateTable extends SchemaChange {
        private final EntityModel entity;
        private final List<RelationModel> inlineForeignKeys;

        public CreateTable(EntityModel entity) {
            this(entity, Collections.emptyList());
        }

        public CreateTable(EntityModel entity, List<RelationModel> inlineForeignKeys) {
            this.entity = entity;
            this.inlineForeignKeys = inlineForeignKeys;
        }

        public EntityModel getEntity() {
            return entity;
        }

        /**
         * The owning relations whose foreign keys are emitted inline with this table. Relations that
         * would create a cyclic dependency are excluded here and emitted as {@link AddForeignKey}.
         */
        public List<RelationModel> getInlineForeignKeys() {
            return inlineForeignKeys;
        }
    }

    public static class AddColumn extends SchemaChange {
        private final String tableName;
        private final FieldModel field;

        public AddColumn(String tableName, FieldModel field) {
            this.tableName = tableName;
            this.field = field;
        }

        public String getTableName() {
            return tableName;
        }

        public FieldModel getField() {
            return field;
        }
    }

    public static class ModifyColumn extends SchemaChange {
        private final String tableName;
        private final FieldModel field;

        public ModifyColumn(String tableName, FieldModel field) {
            this.tableName = tableName;
            this.field = field;
        }

        public String getTableName() {
            return tableName;
        }

        public FieldModel getField() {
            return field;
        }
    }

    public static class CreateIndex extends SchemaChange {
        private final String tableName;
        private final IndexModel index;

        public CreateIndex(String tableName, IndexModel index) {
            this.tableName = tableName;
            this.index = index;
        }

        public String getTableName() {
            return tableName;
        }

        public IndexModel getIndex() {
            return index;
        }
    }

    /**
     * Adds a foreign-key constraint to an existing table via {@code ALTER TABLE}. Used for tables
     * that already exist and for breaking cyclic dependencies between newly created tables.
     */
    public static class AddForeignKey extends SchemaChange {
        private final String tableName;
        private final String constraintName;
        private final String column;
        private final String referencedTable;
        private final String referencedColumn;

        public AddForeignKey(String tableName, String constraintName, String column,
                             String referencedTable, String referencedColumn) {
            this.tableName = tableName;
            this.constraintName = constraintName;
            this.column = column;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }

        public String getTableName() {
            return tableName;
        }

        public String getConstraintName() {
            return constraintName;
        }

        public String getColumn() {
            return column;
        }

        public String getReferencedTable() {
            return referencedTable;
        }

        public String getReferencedColumn() {
            return referencedColumn;
        }
    }
}
