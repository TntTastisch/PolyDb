package de.tnttastisch.polydb.query.sql;

/**
 * The comparison operators a {@link Condition} can express. Binary operators carry their SQL symbol;
 * the shape-changing operators ({@code IN}, {@code BETWEEN}, the {@code NULL} checks) have no single
 * symbol and are rendered specially by {@link Condition}.
 */
public enum Operator {

    EQUALS("="),
    NOT_EQUALS("<>"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    IN(null),
    NOT_IN(null),
    BETWEEN(null),
    IS_NULL(null),
    IS_NOT_NULL(null);

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    /**
     * The infix SQL symbol for a binary operator (e.g. {@code =}, {@code <=}, {@code LIKE}).
     *
     * @return the symbol
     * @throws IllegalStateException for operators without a plain infix form ({@code IN},
     *                               {@code BETWEEN}, the {@code NULL} checks), which are rendered by
     *                               {@link Condition} instead
     */
    public String symbol() {
        if (symbol == null) {
            throw new IllegalStateException(name() + " has no infix symbol");
        }
        return symbol;
    }
}
