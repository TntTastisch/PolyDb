package de.tnttastisch.polydb.query.sql;

/**
 * Sort direction of a single {@link Order}. Kept in the SQL-building package so it can be shared by
 * the low-level {@link SqlBuilder} and the higher-level paging/sorting API (see
 * {@code de.tnttastisch.polydb.query.Sort}) without duplicating the concept.
 */
public enum Direction {

    /** Ascending order ({@code ASC}). */
    ASC,

    /** Descending order ({@code DESC}). */
    DESC;

    /** The SQL keyword for this direction. */
    public String sql() {
        return this == ASC ? "ASC" : "DESC";
    }

    /**
     * Parses a direction from its textual form, accepting {@code "asc"}/{@code "desc"} in any case.
     *
     * @param value the textual direction
     * @return the matching direction
     * @throws IllegalArgumentException if {@code value} is neither ascending nor descending
     */
    public static Direction fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Direction must not be null");
        }
        return switch (value.trim().toUpperCase()) {
            case "ASC" -> ASC;
            case "DESC" -> DESC;
            default -> throw new IllegalArgumentException("Unknown sort direction: " + value);
        };
    }
}
