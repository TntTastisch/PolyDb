package de.tnttastisch.polydb.migration.operation;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;

/**
 * Creates a table with the given columns, optionally declaring some owning foreign keys inline. Mirrors
 * the data the schema comparator needs (columns as {@link FieldModel}s, inline relations), so the same
 * operation serves both automatic and manual migrations. Its inverse is a {@link DropTableOperation}.
 */
public record CreateTableOperation(String tableName, List<FieldModel> fields,
                                   List<RelationModel> inlineForeignKeys) implements MigrationOperation {

    public CreateTableOperation(String tableName, List<FieldModel> fields) {
        this(tableName, fields, List.of());
    }

    @Override
    public List<SqlStatement> toStatements(Dialect dialect) {
        String sql = dialect.getCreateTableSql(tableName, fields, inlineForeignKeys);
        return sql == null ? List.of() : List.of(new SqlStatement(sql));
    }

    @Override
    public String describe() {
        return "Create table " + tableName;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public MigrationOperation reverse() {
        return new DropTableOperation(tableName, this);
    }
}
