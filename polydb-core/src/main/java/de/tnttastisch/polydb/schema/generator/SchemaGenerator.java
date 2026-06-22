package de.tnttastisch.polydb.schema.generator;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.comparison.SchemaChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(SchemaGenerator.class);

    private final Dialect dialect;

    public SchemaGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public List<String> generateSql(List<SchemaChange> changes) {
        List<String> sqlStatements = new ArrayList<>();

        for (SchemaChange change : changes) {
            String sql = toSql(change);
            if (sql != null) {
                sqlStatements.add(sql);
            }
        }

        return sqlStatements;
    }

    private String toSql(SchemaChange change) {
        if (change instanceof SchemaChange.CreateTable create) {
            return dialect.getCreateTableSql(
                    create.getEntity().getTableName(),
                    create.getEntity().getFields(),
                    create.getInlineForeignKeys());
        }
        if (change instanceof SchemaChange.AddColumn add) {
            return dialect.getAddColumnSql(add.getTableName(), add.getField());
        }
        if (change instanceof SchemaChange.ModifyColumn modify) {
            return dialect.getModifyColumnSql(modify.getTableName(), modify.getField());
        }
        if (change instanceof SchemaChange.CreateIndex index) {
            return dialect.getCreateIndexSql(index.getTableName(), index.getIndex());
        }
        if (change instanceof SchemaChange.AddForeignKey fk) {
            if (!dialect.supportsForeignKeys()) {
                return null;
            }
            if (!dialect.supportsAddForeignKeyViaAlter()) {
                log.warn("{} cannot add foreign keys via ALTER; skipping constraint {} on {} "
                                + "(declare it inline at table creation instead)",
                        dialect.getName(), fk.getConstraintName(), fk.getTableName());
                return null;
            }
            return dialect.getAddForeignKeySql(fk.getTableName(), fk.getConstraintName(),
                    fk.getColumn(), fk.getReferencedTable(), fk.getReferencedColumn());
        }
        return null;
    }
}
