package de.tnttastisch.polydb.testmigrations;

import de.tnttastisch.polydb.migration.core.Migration;
import de.tnttastisch.polydb.migration.core.MigrationContext;

import java.sql.Connection;
import java.sql.Statement;

/** Test fixture: a legacy raw-SQL {@link Migration}, to prove the runner still supports the old style. */
public class V002_LegacySeed implements Migration {

    @Override
    public String getVersion() {
        return "002";
    }

    @Override
    public String getDescription() {
        return "legacy insert into accounts";
    }

    @Override
    public void migrate(MigrationContext context) throws Exception {
        try (Connection connection = context.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO accounts (id, name) VALUES (2, 'legacy')");
        }
    }
}
