package de.tnttastisch.polydb.migration.history;

/**
 * The metadata recorded for one migration attempt in the schema-history table: not only the version
 * and outcome, but a checksum (to detect a migration edited after it was applied), the class name, the
 * migration type, execution duration, the PolyDB version that ran it and, on failure, the error.
 *
 * @param version         the migration's version identifier (primary key in history)
 * @param name            the migration class name (for diagnostics)
 * @param description     the human-readable description
 * @param type            {@code "AUTO"}, {@code "MANUAL"} or {@code "LEGACY"}
 * @param checksum        a hash of the migration's content, to detect later modification
 * @param polydbVersion   the PolyDB version that applied it
 * @param executionTimeMs how long the migration took, in milliseconds
 * @param status          {@code "SUCCESS"} or {@code "FAILED"}
 * @param errorMessage    the failure message, or {@code null} on success
 */
public record MigrationRecord(String version, String name, String description, String type,
                              String checksum, String polydbVersion, long executionTimeMs,
                              String status, String errorMessage) {

    /** @return whether this record represents a successful application. */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
