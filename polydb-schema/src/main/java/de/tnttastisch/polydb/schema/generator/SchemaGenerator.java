package de.tnttastisch.polydb.schema.generator;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.comparison.SchemaChange;

import java.util.ArrayList;
import java.util.List;

public class SchemaGenerator {

    private final Dialect dialect;

    public SchemaGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public List<String> generateSql(List<SchemaChange> changes) {
        List<String> sqlStatements = new ArrayList<>();

        for (SchemaChange change : changes) {
            String type = change.getClass().getSimpleName();
            switch (type) {
                case "CreateTable":
                    SchemaChange.CreateTable create = (SchemaChange.CreateTable) change;
                    sqlStatements.add(dialect.getCreateTableSql(create.getEntity().getTableName(), create.getEntity().getFields()));
                    break;
                case "AddColumn":
                    SchemaChange.AddColumn add = (SchemaChange.AddColumn) change;
                    sqlStatements.add(dialect.getAddColumnSql(add.getTableName(), add.getField()));
                    break;
            }
        }

        return sqlStatements;
    }
}
