package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * A composable, reusable filter over entity {@code T}, PolyDB's equivalent of Spring Data's
 * {@code Specification}. A specification turns a {@link Root} into a {@link Condition}; specifications
 * combine with {@link #and}/{@link #or}/{@link #not} to build dynamic {@code WHERE} clauses from
 * optional criteria. A specification (or a combinator operand) may produce {@code null} to mean "no
 * restriction", and the combinators treat {@code null} accordingly.
 *
 * <pre>{@code
 * Specification<User> spec = Specification.<User>where(r -> r.greaterThan("age", 18))
 *         .and(r -> r.equal("active", true));
 * List<User> adults = userRepository.findAll(spec);
 * }</pre>
 *
 * @param <T> the entity type
 */
@FunctionalInterface
public interface Specification<T> {

    /**
     * Builds this specification's predicate.
     *
     * @param root the property-to-condition builder
     * @return the condition, or {@code null} for no restriction
     */
    Condition toCondition(Root<T> root);

    /** A specification that imposes no restriction (matches everything). */
    static <T> Specification<T> unrestricted() {
        return root -> null;
    }

    /** Null-safe identity, for readable chains: {@code Specification.where(a).and(b)}. */
    static <T> Specification<T> where(Specification<T> spec) {
        return spec == null ? unrestricted() : spec;
    }

    /** This specification AND {@code other} ({@code null} operands are ignored). */
    default Specification<T> and(Specification<T> other) {
        return combine(this, other, true);
    }

    /** This specification OR {@code other} ({@code null} operands are ignored). */
    default Specification<T> or(Specification<T> other) {
        return combine(this, other, false);
    }

    /** The negation of {@code spec}. */
    static <T> Specification<T> not(Specification<T> spec) {
        return root -> {
            Condition condition = spec == null ? null : spec.toCondition(root);
            return condition == null ? null : Condition.not(condition);
        };
    }

    /** The AND of all specifications ({@code null}/empty conditions ignored). */
    static <T> Specification<T> allOf(List<Specification<T>> specs) {
        return reduce(specs, true);
    }

    /** The OR of all specifications ({@code null}/empty conditions ignored). */
    static <T> Specification<T> anyOf(List<Specification<T>> specs) {
        return reduce(specs, false);
    }

    private static <T> Specification<T> combine(Specification<T> first, Specification<T> second, boolean and) {
        return root -> {
            Condition a = first == null ? null : first.toCondition(root);
            Condition b = second == null ? null : second.toCondition(root);
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            return and ? Condition.and(List.of(a, b)) : Condition.or(List.of(a, b));
        };
    }

    private static <T> Specification<T> reduce(List<Specification<T>> specs, boolean and) {
        return root -> {
            List<Condition> conditions = new ArrayList<>();
            for (Specification<T> spec : specs) {
                Condition condition = spec == null ? null : spec.toCondition(root);
                if (condition != null) {
                    conditions.add(condition);
                }
            }
            if (conditions.isEmpty()) {
                return null;
            }
            if (conditions.size() == 1) {
                return conditions.get(0);
            }
            return and ? Condition.and(conditions) : Condition.or(conditions);
        };
    }
}
