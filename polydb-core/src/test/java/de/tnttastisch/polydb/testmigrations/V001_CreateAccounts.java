package de.tnttastisch.polydb.testmigrations;

import de.tnttastisch.polydb.migration.core.BaseMigration;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;

import static de.tnttastisch.polydb.migration.plan.MigrationBuilder.row;

/** Test fixture: a declarative {@link BaseMigration} that creates a table and seeds one row. */
public class V001_CreateAccounts extends BaseMigration {

    @Override
    public String getVersion() {
        return "001";
    }

    @Override
    public String getDescription() {
        return "create accounts table";
    }

    @Override
    public void up(MigrationBuilder m) {
        m.createTable("accounts", t -> {
            t.column("id", Integer.class).primaryKey();
            t.string("name", 100).notNull();
        });
        m.seed("accounts").insert(row("id", 1, "name", "root"));
    }
}
