package de.tnttastisch.polydb.schema.db;

import de.tnttastisch.polydb.core.exception.PolyDBException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Introspects a live database through JDBC {@link DatabaseMetaData} and reconstructs its structure
 * as a {@link DatabaseSchema}. This is the "actual state" source for migration: the
 * {@link de.tnttastisch.polydb.schema.comparison.SchemaComparator} diffs the returned schema against
 * the parsed {@link de.tnttastisch.polydb.schema.model.EntityModel entity models} to compute the DDL.
 */
public class DatabaseSchemaReader {

    /**
     * Reads every {@code TABLE} in the connection's current catalog/schema together with its columns
     * and foreign keys. Views and other object types are intentionally excluded so only managed
     * tables are compared. Metadata access failures are wrapped in a {@link PolyDBException} since a
     * partial schema would lead to incorrect migration decisions.
     *
     * @return the reconstructed schema; never {@code null} (empty when the database has no tables).
     */
    public DatabaseSchema readSchema(Connection connection) {
        DatabaseSchema schema = new DatabaseSchema();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaName = connection.getSchema();

            try (ResultSet tables = metaData.getTables(catalog, schemaName, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    TableSchema tableSchema = new TableSchema(tableName);

                    try (ResultSet columns = metaData.getColumns(catalog, schemaName, tableName, null)) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            int dataType = columns.getInt("DATA_TYPE");
                            String typeName = columns.getString("TYPE_NAME");
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            // JDBC reports nullability as an int code and auto-increment as a "YES"/"NO"/""
                            // string; normalise both to plain booleans for the schema model.
                            boolean nullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                            boolean autoIncrement = "YES".equals(columns.getString("IS_AUTOINCREMENT"));

                            tableSchema.addColumn(new ColumnSchema(
                                    columnName, dataType, typeName, columnSize, nullable, autoIncrement
                            ));
                        }
                    }

                    readForeignKeys(metaData, catalog, schemaName, tableName, tableSchema);

                    schema.addTable(tableSchema);
                }
            }

            return schema;
        } catch (SQLException e) {
            throw new PolyDBException("Failed to read database schema", e);
        }
    }

    /**
     * Reads the foreign keys originating from {@code tableName} so existing constraints are not
     * recreated. Failures are tolerated: some drivers do not fully support {@code getImportedKeys}.
     */
    private void readForeignKeys(DatabaseMetaData metaData, String catalog, String schemaName, String tableName, TableSchema tableSchema) {
        try (ResultSet keys = metaData.getImportedKeys(catalog, schemaName, tableName)) {
            while (keys.next()) {
                tableSchema.addForeignKeyColumn(keys.getString("FKCOLUMN_NAME"));
            }
        } catch (SQLException ignored) {
            // Driver does not support imported-key metadata; treat as "no known foreign keys".
        }
    }
}
