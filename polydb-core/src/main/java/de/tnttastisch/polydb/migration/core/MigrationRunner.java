package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.migration.history.HistoryRepository;
import de.tnttastisch.polydb.migration.scanner.MigrationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Orchestrates a migration run: it discovers {@link Migration} implementations on the classpath,
 * filters out those already recorded in the schema-history table, and applies the remaining ones in
 * ascending version order, recording the outcome of each.
 *
 * <p>Each migration runs independently — there is no surrounding "all or nothing" transaction across
 * migrations. A migration that throws aborts the whole run: its failure is logged to the history
 * table and the exception is re-thrown, so already-applied migrations stay applied and the failed
 * (and any later) version is retried on the next start.</p>
 */
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationContext context;
    private final HistoryRepository historyRepository;
    private final MigrationScanner scanner;

    public MigrationRunner(MigrationContext context) {
        this.context = context;
        this.historyRepository = new HistoryRepository(context.getDataSource(), context.getDialect());
        this.scanner = new MigrationScanner();
    }

    /**
     * Applies every pending migration found under {@code migrationPackage}.
     *
     * <p>Steps: ensure the history table exists, read the set of already-applied versions, scan the
     * package for migrations (returned sorted by version), then apply each version that has not yet
     * been applied. Does nothing when there is no JDBC {@code DataSource} (e.g. NoSQL dialects).</p>
     *
     * @param migrationPackage the package to scan for {@link Migration} implementations
     */
    public void run(String migrationPackage) {
        if (context.getDataSource() == null) {
            // No JDBC data source (e.g. a NoSQL dialect); there is nothing to migrate against.
            log.warn("DataSource is null, skipping migration runner for dialect: {}", context.getDialect().getName());
            return;
        }
        historyRepository.ensureHistoryTable();
        Set<String> appliedVersions = historyRepository.getAppliedVersions();

        // Returned already sorted by version, so iterating applies them in chronological order.
        List<Migration> availableMigrations = scanner.scanJavaMigrations(migrationPackage);

        for (Migration migration : availableMigrations) {
            if (appliedVersions.contains(migration.getVersion())) {
                continue; // already applied successfully on a previous run
            }

            log.info("Applying migration {}: {}", migration.getVersion(), migration.getDescription());
            try {
                migration.migrate(context);
                // Record success only after migrate() returns without throwing.
                historyRepository.logMigration(migration.getVersion(), migration.getDescription(), true);
            } catch (Exception e) {
                // Record the failed attempt (success = false) and abort the run; the version is not
                // in the applied set, so it will be retried next time.
                historyRepository.logMigration(migration.getVersion(), migration.getDescription(), false);
                throw new RuntimeException("Migration failed: " + migration.getVersion(), e);
            }
        }
    }
}
