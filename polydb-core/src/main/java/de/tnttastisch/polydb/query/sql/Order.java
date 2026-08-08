package de.tnttastisch.polydb.query.sql;

/**
 * A single {@code ORDER BY} term: a column and a {@link Direction}, optionally case-insensitive. The
 * column name is emitted verbatim (it originates from schema metadata, not user input), so it is not
 * quoted or escaped here.
 */
public final class Order {

    private final String column;
    private final Direction direction;
    private final boolean ignoreCase;

    public Order(String column, Direction direction) {
        this(column, direction, false);
    }

    public Order(String column, Direction direction, boolean ignoreCase) {
        this.column = column;
        this.direction = direction;
        this.ignoreCase = ignoreCase;
    }

    /** Ascending order on {@code column}. */
    public static Order asc(String column) {
        return new Order(column, Direction.ASC);
    }

    /** Descending order on {@code column}. */
    public static Order desc(String column) {
        return new Order(column, Direction.DESC);
    }

    public String getColumn() {
        return column;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /** Renders this term, e.g. {@code created_at DESC} or {@code LOWER(name) ASC}. */
    public String toSql() {
        String col = ignoreCase ? "LOWER(" + column + ")" : column;
        return col + " " + direction.sql();
    }

    @Override
    public String toString() {
        return toSql();
    }
}
