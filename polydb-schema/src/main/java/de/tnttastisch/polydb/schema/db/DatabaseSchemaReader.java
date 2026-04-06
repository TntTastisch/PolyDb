package de.tnttastisch.polydb.schema.db;

import de.tnttastisch.polydb.core.exception.PolyDBException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseSchemaReader {

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
                            boolean nullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                            boolean autoIncrement = "YES".equals(columns.getString("IS_AUTOINCREMENT"));

                            tableSchema.addColumn(new ColumnSchema(
                                    columnName, dataType, typeName, columnSize, nullable, autoIncrement
                            ));
                        }
                    }

                    schema.addTable(tableSchema);
                }
            }

            return schema;
        } catch (SQLException e) {
            throw new PolyDBException("Failed to read database schema", e);
        }
    }
}
