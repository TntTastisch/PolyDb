package de.tnttastisch.polydb.schema.generator;

import de.tnttastisch.polydb.dialect.Dialect;
import de.tnttastisch.polydb.schema.comparison.SchemaChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Final stage of the schema pipeline: renders the ordered list of {@link SchemaChange}s produced by
 * the {@link de.tnttastisch.polydb.schema.comparison.SchemaComparator} into executable DDL strings.
 * It delegates all dialect-specific syntax to the {@link Dialect}, so this class only routes each
 * change type to the matching dialect method and drops changes the dialect cannot express.
 */
public class SchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(SchemaGenerator.class);

    private final Dialect dialect;

    public SchemaGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Renders each change to a DDL statement, in input order so dependencies (e.g. a table before a
     * foreign key referencing it) are emitted correctly.
     *
     * @return the SQL statements to execute; changes that yield no SQL for this dialect are omitted,
     *         so the result may be shorter than {@code changes}.
     */
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

    /**
     * Maps a single change to its DDL via the dialect, or {@code null} when the dialect cannot emit
     * it (e.g. a NoSQL dialect with no foreign keys, or one that cannot add a constraint via
     * {@code ALTER} — in the latter case the constraint should have been declared inline at creation).
     */
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
