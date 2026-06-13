package de.tnttastisch.polydb.migration.history;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class HistoryRepository {

    private final DataSource dataSource;
    private final Dialect dialect;
    private static final String TABLE_NAME = "polydb_schema_history";

    public HistoryRepository(DataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

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
