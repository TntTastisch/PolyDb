package de.tnttastisch.polydb.migration.core;

import de.tnttastisch.polydb.migration.executor.MigrationExecutor;
import de.tnttastisch.polydb.migration.plan.ExecutionMode;
import de.tnttastisch.polydb.migration.plan.MigrationBuilder;
import de.tnttastisch.polydb.migration.plan.MigrationPlan;

import java.sql.Connection;

/**
 * Preferred base class for versioned migrations: instead of writing raw SQL against a
 * {@link MigrationContext}, subclasses describe changes declaratively in {@link #up(MigrationBuilder)}
 * using the fluent {@code MigrationBuilder}. The resulting {@link MigrationPlan} flows through the same
 * {@link MigrationExecutor} as automatic migration, so these migrations gain transactional application,
 * SQL preview, dry-run and (for reversible operations) automatic rollback for free.
 *
 * <p>Override {@link #down(MigrationBuilder)} only for operations that cannot be reversed
 * automatically; simple migrations need no explicit down step.</p>
 */
public abstract class BaseMigration implements Migration {

    /** Describes the forward migration declaratively. */
    public abstract void up(MigrationBuilder migration);

    /** Describes an explicit rollback; defaults to empty (rely on each operation's automatic reverse). */
    public void down(MigrationBuilder migration) {
        // no-op by default
    }

    /** Builds the forward plan by running {@link #up(MigrationBuilder)} into a fresh builder. */
    public final MigrationPlan buildPlan() {
        MigrationBuilder builder = new MigrationBuilder();
        up(builder);
        return builder.build();
    }

    /** Builds the explicit down plan (empty unless {@link #down(MigrationBuilder)} is overridden). */
    public final MigrationPlan buildDownPlan() {
        MigrationBuilder builder = new MigrationBuilder();
        down(builder);
        return builder.build();
    }

    /**
     * Fallback execution path when a {@code BaseMigration} is invoked through the generic
     * {@link Migration#migrate(MigrationContext)} contract (the {@code MigrationRunner} normally applies
     * the {@linkplain #buildPlan() plan} directly so it can control the mode). Applies the forward plan
     * through the executor on a fresh connection.
     */
    @Override
    public void migrate(MigrationContext context) throws Exception {
        try (Connection connection = context.getConnection()) {
            new MigrationExecutor(context.getDialect()).run(buildPlan(), connection, ExecutionMode.EXECUTE);
        }
    }
}
