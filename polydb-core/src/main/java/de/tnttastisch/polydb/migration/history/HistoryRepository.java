package de.tnttastisch.polydb.migration.history;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which {@link de.tnttastisch.polydb.migration.core.Migration migrations} have been applied,
 * backed by the {@value #TABLE_NAME} table. Beyond the original {@code version}/{@code description}/
 * {@code installed_on}/{@code success} columns it records richer metadata (name, type, checksum,
 * PolyDB version, execution time, status and any error) via {@link #record(MigrationRecord)}.
 *
 * <p>The extended columns are added additively: a fresh database gets the full schema, while a database
 * created by an earlier PolyDB version is upgraded in place with {@code ALTER TABLE ... ADD COLUMN}, so
 * existing history is preserved. The {@code success} boolean remains the source of truth for "already
 * applied", keeping {@link #getAppliedVersions()} compatible with rows written by older versions.</p>
 */
public class HistoryRepository {

    private final DataSource dataSource;
    private final Dialect dialect;

    /** Name of the schema-history table managed by PolyDB. */
    private static final String TABLE_NAME = "polydb_schema_history";

    /** Extended (metadata) columns and their portable types, added on top of the original schema. */
    private static final Map<String, String> EXTENDED_COLUMNS = new LinkedHashMap<>();

    static {
        EXTENDED_COLUMNS.put("name", "VARCHAR(200)");
        EXTENDED_COLUMNS.put("type", "VARCHAR(20)");
        EXTENDED_COLUMNS.put("checksum", "VARCHAR(64)");
        EXTENDED_COLUMNS.put("polydb_version", "VARCHAR(50)");
        EXTENDED_COLUMNS.put("execution_time_ms", "BIGINT");
        EXTENDED_COLUMNS.put("status", "VARCHAR(20)");
        EXTENDED_COLUMNS.put("error_message", "VARCHAR(1000)");
    }

    public HistoryRepository(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    /**
     * Creates the history table (with the full schema) if it does not exist, or upgrades an existing
     * table by adding any missing extended columns.
     *
     * @throws PolyDBException if the existence check, creation or upgrade fails
     */
    public void ensureHistoryTable() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // Scope metadata lookups to the connection's current catalog/schema so a same-named history
            // table in another database (visible to the JDBC user) is not mistaken for this database's
            // table. The DDL/DML below uses unqualified names and thus targets this database, so the
            // existence check must be scoped the same way to stay consistent.
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            if (!historyTableExists(meta, catalog, schema)) {
                createHistoryTable(conn);
            } else {
                ensureExtendedColumns(conn, meta, catalog, schema);
            }
        } catch (SQLException e) {
            throw new PolyDBException("Failed to ensure history table", e);
        }
    }

    private boolean historyTableExists(DatabaseMetaData meta, String catalog, String schema) throws SQLException {
        String pattern = storagePattern(meta);
        try (ResultSet rs = meta.getTables(catalog, schema, pattern, new String[]{"TABLE"})) {
            while (rs.next()) {
                if (TABLE_NAME.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Normalises {@link #TABLE_NAME} to the backend's identifier storage convention so metadata lookups
     * match: lower-cased on PostgreSQL, upper-cased on Oracle/H2/DB2, unchanged otherwise.
     */
    private String storagePattern(DatabaseMetaData meta) throws SQLException {
        if (meta.storesLowerCaseIdentifiers()) {
            return TABLE_NAME.toLowerCase();
        }
        if (meta.storesUpperCaseIdentifiers()) {
            return TABLE_NAME.toUpperCase();
        }
        return TABLE_NAME;
    }

    /** Issues the DDL for the full history table (original columns plus the extended metadata columns). */
    private void createHistoryTable(Connection conn) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE " + TABLE_NAME + " (" +
                "version VARCHAR(50) PRIMARY KEY, " +
                "description VARCHAR(200), " +
                "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "success BOOLEAN");
        for (Map.Entry<String, String> column : EXTENDED_COLUMNS.entrySet()) {
            sql.append(", ").append(column.getKey()).append(" ").append(column.getValue());
        }
        sql.append(")");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    /** Adds any extended column that is not yet present, preserving existing history rows. */
    private void ensureExtendedColumns(Connection conn, DatabaseMetaData meta, String catalog, String schema) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, storagePattern(meta), null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        for (Map.Entry<String, String> column : EXTENDED_COLUMNS.entrySet()) {
            if (!existing.contains(column.getKey())) {
                String sql = "ALTER TABLE " + TABLE_NAME + " ADD " + column.getKey() + " " + column.getValue();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }
        }
    }

    /**
     * Returns the set of versions that have been applied <em>successfully</em>. A previously failed
     * version (success = false) is not included and will be retried. A query failure (e.g. the table
     * not existing yet) is swallowed and treated as "nothing applied".
     */
    public Set<String> getAppliedVersions() {
        Set<String> versions = new HashSet<>();
        String sql = "SELECT version FROM " + TABLE_NAME + " WHERE success = true";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                versions.add(rs.getString("version"));
            }
        } catch (SQLException e) {
            // Table might not exist yet
        }
        return versions;
    }

    /**
     * Inserts a row recording the outcome of a migration attempt, including the extended metadata. The
     * {@code success} boolean is derived from {@link MigrationRecord#status()} so older readers keep
     * working.
     *
     * @throws PolyDBException if the insert fails
     */
    public void record(MigrationRecord record) {
        String sql = "INSERT INTO " + TABLE_NAME +
                " (version, description, success, name, type, checksum, polydb_version, execution_time_ms, status, error_message)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.version());
            pstmt.setString(2, truncate(record.description(), 200));
            pstmt.setBoolean(3, record.isSuccess());
            pstmt.setString(4, truncate(record.name(), 200));
            pstmt.setString(5, record.type());
            pstmt.setString(6, record.checksum());
            pstmt.setString(7, record.polydbVersion());
            pstmt.setLong(8, record.executionTimeMs());
            pstmt.setString(9, record.status());
            pstmt.setString(10, truncate(record.errorMessage(), 1000));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new PolyDBException("Failed to record migration " + record.version(), e);
        }
    }

    /**
     * Backwards-compatible shorthand that records only version, description and success. Retained for
     * callers that predate {@link #record(MigrationRecord)}.
     */
    public void logMigration(String version, String description, boolean success) {
        record(new MigrationRecord(version, null, description, "LEGACY", null,
                null, 0L, success ? "SUCCESS" : "FAILED", null));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
