package de.tnttastisch.polydb.query.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A boolean predicate that renders to a parameterised SQL {@code WHERE} fragment. A condition tree is
 * built from {@link Comparison leaf comparisons} combined with {@link #and(List) AND}/{@link #or(List)
 * OR} composites and {@link #not(Condition) negation}; the whole tree renders with {@code ?}
 * placeholders and its bind values are collected separately, in placeholder order, via
 * {@link #collectParameters(List)}.
 *
 * <p>Column names are inlined verbatim (they come from schema metadata, never from user input), so
 * they are not quoted or escaped. Only bind <em>values</em> flow through placeholders. Conditions are
 * immutable and therefore safe to reuse. This is the shared substrate for derived query methods and
 * {@code Specification} filters.</p>
 */
public interface Condition {

    /** Renders this predicate as SQL using {@code ?} placeholders for every bind value. */
    String toSql();

    /**
     * Appends this predicate's bind values to {@code target}, in the same left-to-right order as the
     * {@code ?} placeholders produced by {@link #toSql()}.
     *
     * @param target the list to append bind values to
     */
    void collectParameters(List<Object> target);

    // ------------------------------------------------------------------ leaf factories

    /** {@code column = value}. */
    static Condition eq(String column, Object value) {
        return new Comparison(column, Operator.EQUALS, List.of(value), false);
    }

    /** {@code column = value}, case-insensitive via {@code LOWER(...)} on both sides. */
    static Condition eqIgnoreCase(String column, Object value) {
        return new Comparison(column, Operator.EQUALS, List.of(value), true);
    }

    /** {@code column <> value}. */
    static Condition ne(String column, Object value) {
        return new Comparison(column, Operator.NOT_EQUALS, List.of(value), false);
    }

    /** {@code column < value}. */
    static Condition lt(String column, Object value) {
        return new Comparison(column, Operator.LESS_THAN, List.of(value), false);
    }

    /** {@code column <= value}. */
    static Condition lte(String column, Object value) {
        return new Comparison(column, Operator.LESS_THAN_OR_EQUAL, List.of(value), false);
    }

    /** {@code column > value}. */
    static Condition gt(String column, Object value) {
        return new Comparison(column, Operator.GREATER_THAN, List.of(value), false);
    }

    /** {@code column >= value}. */
    static Condition gte(String column, Object value) {
        return new Comparison(column, Operator.GREATER_THAN_OR_EQUAL, List.of(value), false);
    }

    /** {@code column LIKE pattern}. The caller supplies the pattern including any {@code %} wildcards. */
    static Condition like(String column, Object pattern) {
        return new Comparison(column, Operator.LIKE, List.of(pattern), false);
    }

    /** {@code column LIKE pattern}, case-insensitive. */
    static Condition likeIgnoreCase(String column, Object pattern) {
        return new Comparison(column, Operator.LIKE, List.of(pattern), true);
    }

    /** {@code column NOT LIKE pattern}. */
    static Condition notLike(String column, Object pattern) {
        return new Comparison(column, Operator.NOT_LIKE, List.of(pattern), false);
    }

    /**
     * {@code column IN (v1, v2, ...)}. An empty collection renders as {@code 1 = 0} (matches nothing)
     * so the surrounding query stays valid.
     */
    static Condition in(String column, Collection<?> values) {
        return new Comparison(column, Operator.IN, new ArrayList<>(values), false);
    }

    /**
     * {@code column NOT IN (v1, v2, ...)}. An empty collection renders as {@code 1 = 1} (matches
     * everything), the complement of {@link #in(String, Collection)}.
     */
    static Condition notIn(String column, Collection<?> values) {
        return new Comparison(column, Operator.NOT_IN, new ArrayList<>(values), false);
    }

    /** {@code column BETWEEN low AND high}. */
    static Condition between(String column, Object low, Object high) {
        return new Comparison(column, Operator.BETWEEN, List.of(low, high), false);
    }

    /** {@code column IS NULL}. */
    static Condition isNull(String column) {
        return new Comparison(column, Operator.IS_NULL, List.of(), false);
    }

    /** {@code column IS NOT NULL}. */
    static Condition isNotNull(String column) {
        return new Comparison(column, Operator.IS_NOT_NULL, List.of(), false);
    }

    // ------------------------------------------------------------------ composite factories

    /**
     * Combines conditions with {@code AND}. A single-element list is returned unwrapped; an empty
     * list is rejected.
     */
    static Condition and(List<Condition> parts) {
        return combine(parts, true);
    }

    /**
     * Combines conditions with {@code OR}. A single-element list is returned unwrapped; an empty list
     * is rejected.
     */
    static Condition or(List<Condition> parts) {
        return combine(parts, false);
    }

    /** Negates a condition: {@code NOT (inner)}. */
    static Condition not(Condition inner) {
        return new Negation(inner);
    }

    private static Condition combine(List<Condition> parts, boolean and) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Cannot combine an empty condition list");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return new Composite(new ArrayList<>(parts), and);
    }

    // ------------------------------------------------------------------ implementations

    /** A leaf comparison of a column against zero or more bind values. */
    final class Comparison implements Condition {

        private final String column;
        private final Operator operator;
        private final List<Object> values;
        private final boolean ignoreCase;

        Comparison(String column, Operator operator, List<Object> values, boolean ignoreCase) {
            this.column = column;
            this.operator = operator;
            this.values = List.copyOf(values);
            this.ignoreCase = ignoreCase;
        }

        @Override
        public String toSql() {
            return switch (operator) {
                case IS_NULL -> column + " IS NULL";
                case IS_NOT_NULL -> column + " IS NOT NULL";
                case IN -> inSql(false);
                case NOT_IN -> inSql(true);
                case BETWEEN -> lhs() + " BETWEEN " + placeholder() + " AND " + placeholder();
                default -> lhs() + " " + operator.symbol() + " " + placeholder();
            };
        }

        private String inSql(boolean negate) {
            if (values.isEmpty()) {
                return negate ? "1 = 1" : "1 = 0";
            }
            String placeholders = values.stream().map(v -> placeholder()).collect(Collectors.joining(", "));
            return lhs() + (negate ? " NOT IN (" : " IN (") + placeholders + ")";
        }

        private String lhs() {
            return ignoreCase ? "LOWER(" + column + ")" : column;
        }

        private String placeholder() {
            return ignoreCase ? "LOWER(?)" : "?";
        }

        @Override
        public void collectParameters(List<Object> target) {
            target.addAll(values);
        }
    }

    /** An {@code AND}/{@code OR} of two or more child conditions, wrapped in parentheses. */
    final class Composite implements Condition {

        private final List<Condition> parts;
        private final boolean and;

        Composite(List<Condition> parts, boolean and) {
            this.parts = parts;
            this.and = and;
        }

        @Override
        public String toSql() {
            String joiner = and ? " AND " : " OR ";
            return "(" + parts.stream().map(Condition::toSql).collect(Collectors.joining(joiner)) + ")";
        }

        @Override
        public void collectParameters(List<Object> target) {
            for (Condition part : parts) {
                part.collectParameters(target);
            }
        }
    }

    /** A negated condition: {@code NOT (inner)}. */
    final class Negation implements Condition {

        private final Condition inner;

        Negation(Condition inner) {
            this.inner = inner;
        }

        @Override
        public String toSql() {
            return "NOT (" + inner.toSql() + ")";
        }

        @Override
        public void collectParameters(List<Object> target) {
            inner.collectParameters(target);
        }
    }
}
