package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;

public abstract class SchemaChange {

    public static class CreateTable extends SchemaChange {
        private final EntityModel entity;

        public CreateTable(EntityModel entity) {
            this.entity = entity;
        }

        public EntityModel getEntity() {
            return entity;
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
}
