package de.tnttastisch.polydb.migration.precondition;

import de.tnttastisch.polydb.schema.db.DatabaseSchema;
import de.tnttastisch.polydb.schema.db.TableSchema;

import java.util.function.Predicate;

/**
 * Factory of common {@link Precondition}s. Import these statically for readable migration guards:
 * {@code m.preconditions(ifTableMissing("profiles"), ifColumnExists("users", "email"))}.
 */
public final class Preconditions {

    private Preconditions() {
    }

    /** The migration runs only if the table already exists. */
    public static Precondition ifTableExists(String table) {
        return of("table '" + table + "' exists", schema -> schema.getTable(table) != null);
    }

    /** The migration runs only if the table does not yet exist. */
    public static Precondition ifTableMissing(String table) {
        return of("table '" + table + "' is missing", schema -> schema.getTable(table) == null);
    }

    /** The migration runs only if the column exists on the table. */
    public static Precondition ifColumnExists(String table, String column) {
        return of("column '" + table + "." + column + "' exists", schema -> hasColumn(schema, table, column));
    }

    /** The migration runs only if the column does not exist on the table. */
    public static Precondition ifColumnMissing(String table, String column) {
        return of("column '" + table + "." + column + "' is missing", schema -> !hasColumn(schema, table, column));
    }

    /** The migration runs only if an index of the given name exists on the table. */
    public static Precondition ifIndexExists(String table, String indexName) {
        return of("index '" + indexName + "' on '" + table + "' exists", schema -> {
            TableSchema t = schema.getTable(table);
            return t != null && t.hasIndex(indexName);
        });
    }

    private static boolean hasColumn(DatabaseSchema schema, String table, String column) {
        TableSchema t = schema.getTable(table);
        return t != null && t.getColumns().containsKey(column.toLowerCase());
    }

    private static Precondition of(String description, Predicate<DatabaseSchema> test) {
        return new Precondition() {
            @Override
            public boolean isMet(DatabaseSchema schema) {
                return test.test(schema);
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }
}
