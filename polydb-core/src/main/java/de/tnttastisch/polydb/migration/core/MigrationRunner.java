package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.migration.executor.MigrationExecutor;
import de.tnttastisch.polydb.migration.history.HistoryRepository;
import de.tnttastisch.polydb.migration.history.MigrationRecord;
import de.tnttastisch.polydb.migration.plan.ExecutionMode;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;
import de.tnttastisch.polydb.migration.scanner.MigrationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates a migration run: it discovers {@link Migration} implementations on the classpath,
 * filters out those already recorded in the schema-history table, and applies the remaining ones in
 * ascending version order through the shared {@link MigrationExecutor}, recording rich metadata for each.
 *
 * <p>Two kinds of migration are supported. A {@link BaseMigration} contributes a declarative
 * {@link MigrationPlan}, so it benefits from transactional application, dry-run and SQL preview. A
 * legacy {@link Migration} that writes raw SQL via {@link Migration#migrate(MigrationContext)} still
 * runs as before (it manages its own connections), but cannot be previewed or dry-run.</p>
 *
 * <p>Each migration runs independently — there is no surrounding "all or nothing" transaction across
 * migrations. A migration that throws aborts the whole run: its failure is recorded in the history
 * table and the exception is re-thrown, so the failed (and any later) version is retried on the next
 * start.</p>
 */
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationContext context;
    private final HistoryRepository historyRepository;
    private final MigrationScanner scanner;
    private final MigrationExecutor executor;

    public MigrationRunner(MigrationContext context) {
        this.context = context;
        this.historyRepository = new HistoryRepository(context.getDataSource(), context.getDialect());
        this.scanner = new MigrationScanner();
        this.executor = new MigrationExecutor(context.getDialect());
    }

    /** Applies every pending migration under {@code migrationPackage} in {@link ExecutionMode#EXECUTE}. */
    public void run(String migrationPackage) {
        run(migrationPackage, ExecutionMode.EXECUTE);
    }

    /**
     * Applies (or, in dry-run/preview mode, plans) every pending migration found under
     * {@code migrationPackage}. Does nothing when there is no JDBC {@code DataSource} (e.g. NoSQL).
     *
     * @param migrationPackage the package to scan for {@link Migration} implementations
     * @param mode             whether to execute the migrations or only plan/preview them
     */
    public void run(String migrationPackage, ExecutionMode mode) {
        if (context.getDataSource() == null) {
            log.warn("DataSource is null, skipping migration runner for dialect: {}", context.getDialect().getName());
            return;
        }
        historyRepository.ensureHistoryTable();
        Set<String> appliedVersions = historyRepository.getAppliedVersions();

        List<Migration> availableMigrations = scanner.scanJavaMigrations(migrationPackage);

        for (Migration migration : availableMigrations) {
            if (appliedVersions.contains(migration.getVersion())) {
                continue; // already applied successfully on a previous run
            }
            applyOne(migration, mode);
        }
    }

    private void applyOne(Migration migration, ExecutionMode mode) {
        if (migration instanceof BaseMigration baseMigration) {
            applyPlanned(baseMigration, mode);
        } else {
            applyLegacy(migration, mode);
        }
    }

    /** Applies a declarative {@link BaseMigration} through the executor, recording the outcome. */
    private void applyPlanned(BaseMigration migration, ExecutionMode mode) {
        String version = migration.getVersion();
        String description = migration.getDescription();
        MigrationPlan plan = migration.buildPlan();
        String checksum = checksum(version + "|" + description + "|" + plan.describe());

        if (mode == ExecutionMode.EXECUTE) {
            log.info("Applying migration {}: {}", version, description);
        } else {
            log.info("[{}] migration {}: {}\n{}", mode, version, description, plan.describe());
        }

        long start = System.nanoTime();
        try {
            try (Connection connection = context.getConnection()) {
                executor.run(plan, connection, mode);
            }
        } catch (Exception e) {
            recordOutcome(mode, migration, "MANUAL", checksum, elapsedMs(start), "FAILED", e.getMessage());
            throw new RuntimeException("Migration failed: " + version, e);
        }
        recordOutcome(mode, migration, "MANUAL", checksum, elapsedMs(start), "SUCCESS", null);
    }

    /** Runs a legacy raw-SQL migration; it cannot be previewed, so dry-run mode skips it (unrecorded). */
    private void applyLegacy(Migration migration, ExecutionMode mode) {
        String version = migration.getVersion();
        String description = migration.getDescription();
        String checksum = checksum(version + "|" + description + "|legacy");

        if (mode != ExecutionMode.EXECUTE) {
            log.info("[{}] legacy migration {} writes raw SQL and cannot be previewed; skipping", mode, version);
            return;
        }

        log.info("Applying migration {}: {}", version, description);
        long start = System.nanoTime();
        try {
            migration.migrate(context);
        } catch (Exception e) {
            recordOutcome(mode, migration, "LEGACY", checksum, elapsedMs(start), "FAILED", e.getMessage());
            throw new RuntimeException("Migration failed: " + version, e);
        }
        recordOutcome(mode, migration, "LEGACY", checksum, elapsedMs(start), "SUCCESS", null);
    }

    /** Records the outcome, but only when actually executing (dry-run/preview leave history untouched). */
    private void recordOutcome(ExecutionMode mode, Migration migration, String type, String checksum,
                               long elapsedMs, String status, String error) {
        if (mode != ExecutionMode.EXECUTE) {
            return;
        }
        historyRepository.record(new MigrationRecord(
                migration.getVersion(),
                migration.getClass().getSimpleName(),
                migration.getDescription(),
                type,
                checksum,
                polydbVersion(),
                elapsedMs,
                status,
                error));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String polydbVersion() {
        String version = MigrationRunner.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /** A stable SHA-256 hex digest of the migration's content, used to detect later modification. */
    private static String checksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
