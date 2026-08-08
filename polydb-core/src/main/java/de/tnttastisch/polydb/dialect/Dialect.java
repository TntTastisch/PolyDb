package de.tnttastisch.polydb.dialect;

import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.IndexModel;
import de.tnttastisch.polydb.schema.model.RelationModel;

import java.util.List;

/**
 * Database-specific strategy that translates PolyDB's abstract schema model into the concrete DDL
 * (or, for NoSQL stores, the equivalent commands) of a single backend.
 *
 * <p>SQL backends typically extend {@link AbstractSqlDialect}, which supplies standards-based
 * defaults and lets a concrete dialect override only what its vendor does differently — type
 * mappings, identifier quoting, auto-increment/identity syntax, the {@code MODIFY COLUMN} form,
 * and so on. NoSQL backends (e.g. {@link MongoDialect}, {@link CassandraDialect}) implement this
 * interface directly and leave the foreign-key methods as no-ops.
 */
public interface Dialect {

    /** Human-readable name of the target database, e.g. {@code "PostgreSQL"}. */
    String getName();

    /** Maps a field's Java type to the backend's column type, honouring length and auto-increment. */
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

    // ------------------------------------------------------------------ paging

    /**
     * Renders the row-limiting clause appended to a {@code SELECT}, e.g. {@code LIMIT 10 OFFSET 20}.
     * Both bounds are optional; either or both may be {@code null}, and when both are {@code null} an
     * empty string is returned so no clause is appended.
     *
     * <p>The values are inlined as integer literals rather than bound as parameters: they originate
     * from trusted paging inputs (never from user-supplied strings), and inlining keeps the clause
     * self-contained across dialects whose {@code LIMIT}/{@code OFFSET} syntax differs. The default
     * emits the standard {@code LIMIT ... OFFSET ...} form; dialects whose syntax differs (Oracle,
     * SQL Server, DB2, Firebird) override this.</p>
     *
     * @param limit  the maximum number of rows, or {@code null} for no cap
     * @param offset the number of leading rows to skip, or {@code null} for none
     * @return the clause without a leading space, or an empty string when neither bound is set
     */
    default String getLimitClause(Long limit, Long offset) {
        StringBuilder clause = new StringBuilder();
        if (limit != null) {
            clause.append("LIMIT ").append(limit);
            if (offset != null) {
                clause.append(" OFFSET ").append(offset);
            }
        } else if (offset != null) {
            clause.append("OFFSET ").append(offset);
        }
        return clause.toString();
    }

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
