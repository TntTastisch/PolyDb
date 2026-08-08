package de.tnttastisch.polydb.migration.plan;

/**
 * How a {@link MigrationPlan} is processed by the migration executor.
 */
public enum ExecutionMode {

    /** Render each operation to SQL and apply it to the database. */
    EXECUTE,

    /** Compute the plan and render its SQL, but make no changes to the database. */
    DRY_RUN,

    /** Like {@link #DRY_RUN}, geared towards producing an exportable SQL script for inspection. */
    PREVIEW
}
