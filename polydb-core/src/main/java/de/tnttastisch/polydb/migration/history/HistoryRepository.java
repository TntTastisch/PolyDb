package de.tnttastisch.polydb.migration.history;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which {@link de.tnttastisch.polydb.migration.core.Migration migrations} have been applied,
 * backed by the {@value #TABLE_NAME} table. Each row records a {@code version} (primary key), its
 * {@code description}, the {@code installed_on} timestamp and a {@code success} flag, so the
 * {@link de.tnttastisch.polydb.migration.core.MigrationRunner} can skip versions that have already
 * run and retry ones that previously failed.
 */
public class HistoryRepository {

    private final DataSource dataSource;
    private final Dialect dialect;

    /** Name of the schema-history table managed by PolyDB. */
    private static final String TABLE_NAME = "polydb_schema_history";

    public HistoryRepository(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    /**
     * Creates the history table if it does not already exist. The presence check uses JDBC metadata
     * with an upper-cased table name to match databases that store identifiers in upper case.
     *
     * @throws PolyDBException if the existence check or creation fails
     */
    public void ensureHistoryTable() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, TABLE_NAME.toUpperCase(), null)) {
                if (!rs.next()) {
                    createHistoryTable(conn);
                }
            }
        } catch (SQLException e) {
            throw new PolyDBException("Failed to ensure history table", e);
        }
    }

    /** Issues the DDL for the history table using portable column types understood by all dialects. */
    private void createHistoryTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE " + TABLE_NAME + " (" +
                "version VARCHAR(50) PRIMARY KEY, " +
                "description VARCHAR(200), " +
                "installed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "success BOOLEAN" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Returns the set of versions that have been applied <em>successfully</em>. Only these are
     * skipped by the runner, so a previously failed version (success = false) is not included and
     * will be retried. A query failure (e.g. the table not existing yet) is swallowed and treated as
     * "nothing applied".
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
     * Inserts a row recording the outcome of a migration attempt. Called once per migration by the
     * runner: with {@code success = true} after a clean apply, or {@code success = false} when the
     * migration threw.
     *
     * @throws PolyDBException if the insert fails
     */
    public void logMigration(String version, String description, boolean success) {
        String sql = "INSERT INTO " + TABLE_NAME + " (version, description, success) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, version);
            pstmt.setString(2, description);
            pstmt.setBoolean(3, success);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new PolyDBException("Failed to log migration", e);
        }
    }
}
