package de.tnttastisch.polydb.migration.core;

/**
 * A single, versioned change to the database that PolyDB applies exactly once. Implementations are
 * discovered on the classpath by the {@link de.tnttastisch.polydb.migration.scanner.MigrationScanner}
 * (so they must expose a public no-argument constructor) and executed by the
 * {@link MigrationRunner} in ascending {@linkplain #getVersion() version} order.
 *
 * <p>Migrations run after PolyDB has synced the entity-derived schema and are intended for changes
 * the schema generator cannot express on its own (data back-fills, seed data, custom DDL, etc.).
 * Each successfully applied version is recorded in the schema-history table and is never re-run.</p>
 */
public interface Migration {

    /**
     * The unique version identifier that determines apply order and history tracking. Versions are
     * compared lexicographically (see {@link de.tnttastisch.polydb.migration.scanner.MigrationScanner}),
     * so use a zero-padded / sortable scheme (e.g. {@code "001"}, {@code "20240601_1200"}) to keep
     * the natural string order equal to the intended chronological order.
     */
    String getVersion();

    /** Human-readable summary stored alongside the version in the history table and emitted to logs. */
    String getDescription();

    /**
     * Performs the migration. Receives a {@link MigrationContext} granting access to the
     * {@code DataSource}, JDBC {@link java.sql.Connection}s and the active dialect. Any thrown
     * exception aborts the migration run; the failure is recorded in the history table and
     * propagated, so the version will be retried on the next start.
     */
    void migrate(MigrationContext context) throws Exception;

}
