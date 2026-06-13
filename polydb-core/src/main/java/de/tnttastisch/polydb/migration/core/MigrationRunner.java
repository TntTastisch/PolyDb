package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.migration.history.HistoryRepository;
import de.tnttastisch.polydb.migration.scanner.MigrationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

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

    public void run(String migrationPackage) {
        if (context.getDataSource() == null) {
            log.warn("DataSource is null, skipping migration runner for dialect: {}", context.getDialect().getName());
            return;
        }
        historyRepository.ensureHistoryTable();
        Set<String> appliedVersions = historyRepository.getAppliedVersions();

        List<Migration> availableMigrations = scanner.scanJavaMigrations(migrationPackage);

        for (Migration migration : availableMigrations) {
            if (appliedVersions.contains(migration.getVersion())) {
                continue;
            }

            log.info("Applying migration {}: {}", migration.getVersion(), migration.getDescription());
            try {
                migration.migrate(context);
                historyRepository.logMigration(migration.getVersion(), migration.getDescription(), true);
            } catch (Exception e) {
                historyRepository.logMigration(migration.getVersion(), migration.getDescription(), false);
                throw new RuntimeException("Migration failed: " + migration.getVersion(), e);
            }
        }
    }
}
