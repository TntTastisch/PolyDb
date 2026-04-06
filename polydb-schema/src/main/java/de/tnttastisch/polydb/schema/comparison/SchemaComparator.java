package de.tnttastisch.polydb.schema.comparison;

import de.tnttastisch.polydb.schema.db.ColumnSchema;
import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.TableSchema;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.ArrayList;
import java.util.List;

public class SchemaComparator {

    public List<SchemaChange> compare(List<EntityModel> entities, DatabaseSchema dbSchema) {
        List<SchemaChange> changes = new ArrayList<>();

        for (EntityModel entity : entities) {
            TableSchema dbTable = dbSchema.getTable(entity.getTableName());

            if (dbTable == null) {
                changes.add(new SchemaChange.CreateTable(entity));
            } else {
                compareTable(entity, dbTable, changes);
            }
        }

        return changes;
    }

    private void compareTable(EntityModel entity, TableSchema dbTable, List<SchemaChange> changes) {
        for (FieldModel field : entity.getFields()) {
            ColumnSchema dbColumn = dbTable.getColumns().get(field.getColumnName().toLowerCase());

            if (dbColumn == null) {
                changes.add(new SchemaChange.AddColumn(entity.getTableName(), field));
            }
        }
    }
}
