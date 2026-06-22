package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;

public interface Dialect {

    String getName();

    String getSqlType(FieldModel field);

    /**
     * Builds the {@code CREATE TABLE} statement. Owning relations are realised inline by SQL
     * dialects (foreign-key constraints); NoSQL dialects ignore them.
     */
    String getCreateTableSql(String tableName, List<FieldModel> fields, List<RelationModel> relations);

    String getAddColumnSql(String tableName, FieldModel field);

    String getModifyColumnSql(String tableName, FieldModel field);

    String getDropColumnSql(String tableName, String columnName);

    String getCreateIndexSql(String tableName, IndexModel index);

    String getDropIndexSql(String tableName, String indexName);

    String getDropTableSql(String tableName);

    String quoteIdentifier(String identifier);

    // ------------------------------------------------------------------ foreign keys

    /**
     * Whether this store supports foreign-key constraints. NoSQL dialects return {@code false}.
     */
    boolean supportsForeignKeys();

    /**
     * Whether foreign keys can be added to an existing table via {@code ALTER TABLE ADD CONSTRAINT}.
     * SQLite returns {@code false} (foreign keys can only be declared inline at table creation).
     */
    boolean supportsAddForeignKeyViaAlter();

    /**
     * Inline foreign-key definition for use inside a {@code CREATE TABLE} statement, e.g.
     * {@code CONSTRAINT fk_x FOREIGN KEY (col) REFERENCES ref (id)}.
     */
    String getForeignKeyDefinition(String constraintName, String column, String refTable, String refColumn);

    /**
     * {@code ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY ...} form.
     */
    String getAddForeignKeySql(String tableName, String constraintName, String column, String refTable, String refColumn);

    /**
     * A statement that must be executed per connection to enable foreign-key enforcement, or
     * {@code null} when none is required. SQLite returns {@code PRAGMA foreign_keys = ON}.
     */
    String getEnableForeignKeysStatement();
}
