package de.tnttastisch.polydb.examples.entity.migrations;

import de.tnttastisch.polydb.migration.core.Migration;
import de.tnttastisch.polydb.migration.core.MigrationContext;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Example migration that seeds initial data.
 *
 * <p>A migration is a versioned, ordered unit of work PolyDB runs once against the database (it
 * implements {@link Migration} and is discovered/applied during start-up). Schema generation creates
 * the tables from the entity definitions; migrations like this one then run on top of that schema to
 * insert or transform data. PolyDB orders migrations by {@link #getVersion()} and records which have
 * already been applied, so each runs exactly once.</p>
 *
 * <p>This particular migration seeds a single fixed "SYSTEM" user into the {@code users} table so
 * the application always has a known account to fall back on.</p>
 */
public class V1_InitialDataMigration implements Migration {

    /** Ordering key. Migrations are applied in ascending version order and tracked individually. */
    @Override
    public String getVersion() {
        return "1";
    }

    /** Human-readable label recorded alongside the applied version. */
    @Override
    public String getDescription() {
        return "Inserts initial system user";
    }

    /**
     * Performs the migration. The {@link MigrationContext} hands us the {@link javax.sql.DataSource}
     * to obtain a JDBC connection; here we simply run a single INSERT to create the system user.
     * Try-with-resources guarantees the connection and statement are closed.
     */
    @Override
    public void migrate(MigrationContext context) throws Exception {
        try (Connection conn = context.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            // Insert a fixed-id system account that the rest of the application can rely on.
            stmt.execute("INSERT INTO users (id, username, email, created_at) VALUES ('00000000-0000-0000-0000-000000000000', 'SYSTEM', 'system@polydb.org', NOW())");
        }
    }
}
