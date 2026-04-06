package de.tnttastisch.polydb.examples.entity.migrations;

import de.tnttastisch.polydb.migration.core.Migration;
import de.tnttastisch.polydb.migration.core.MigrationContext;

import java.sql.Connection;
import java.sql.Statement;

public class V1_InitialDataMigration implements Migration {

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getDescription() {
        return "Inserts initial system user";
    }

    @Override
    public void migrate(MigrationContext context) throws Exception {
        try (Connection conn = context.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, username, email, created_at) VALUES ('00000000-0000-0000-0000-000000000000', 'SYSTEM', 'system@polydb.org', NOW())");
        }
    }
}
