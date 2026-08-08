package de.tnttastisch.polydb.examples.entity.migrations;

import de.tnttastisch.polydb.migration.core.BaseMigration;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static de.tnttastisch.polydb.migration.plan.MigrationBuilder.row;

/**
 * Example migration that seeds initial data, written against the declarative migration API.
 *
 * <p>Rather than issuing raw SQL, it extends {@link BaseMigration} and describes the change in
 * {@link #up(MigrationBuilder)} using the fluent {@code MigrationBuilder}: PolyDB turns the resulting
 * operation into dialect-appropriate, parameterised SQL and applies it through the same executor as
 * automatic migration (so this migration also participates in dry-run and SQL preview). Auto-migration
 * creates the {@code users} table from the entity definitions; this migration then seeds it.</p>
 *
 * <p>It inserts a single fixed "SYSTEM" user so the application always has a known account to fall back
 * on. Legacy migrations that implement {@code Migration} and write raw SQL directly remain supported.</p>
 */
public class V1_InitialDataMigration extends BaseMigration {

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
     * Seeds the fixed system account declaratively. The values are bound as SQL parameters, so no
     * hand-written, dialect-specific {@code INSERT} is required.
     */
    @Override
    public void up(MigrationBuilder m) {
        m.seed("users").insert(row(
                "id", UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "username", "SYSTEM",
                "email", "system@polydb.org",
                "created_at", LocalDateTime.now()
        ));
    }
}
