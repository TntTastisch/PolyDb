package de.tnttastisch.polydb.query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QueryExecutor {

    private final DataSource dataSource;

    public QueryExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> List<T> executeQuery(String sql, List<Object> params, ResultMapper<T> mapper) {
        List<T> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query execution failed", e);
        }
        return results;
    }

    public int executeUpdate(String sql, List<Object> params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update execution failed", e);
        }
    }
}
