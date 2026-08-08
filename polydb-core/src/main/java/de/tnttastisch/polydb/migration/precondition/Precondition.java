package de.tnttastisch.polydb.migration.precondition;

import de.tnttastisch.polydb.schema.db.DatabaseSchema;

/**
 * A guard evaluated against the live database structure before a migration runs. If any of a
 * migration's preconditions is not met, the engine skips the whole migration (rather than failing),
 * which makes migrations robust against legacy databases where an object may already exist or be
 * absent. Obtain instances from {@link Preconditions}.
 */
public interface Precondition {

    /** @return whether this condition holds for the given introspected {@code schema}. */
    boolean isMet(DatabaseSchema schema);

    /** Human-readable description used in skip logs and plans, e.g. {@code "table 'users' is missing"}. */
    String describe();
}
