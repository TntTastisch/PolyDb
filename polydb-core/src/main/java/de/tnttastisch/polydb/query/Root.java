package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Condition;

import java.util.Collection;

/**
 * The entry point a {@link Specification} uses to build predicates over an entity's <em>properties</em>
 * (Java field names). Each method resolves the property to its column, converts the value the way the
 * persistence layer expects (enums by name, owning relations by foreign-key id), and returns a
 * {@link Condition}. This is PolyDB's lightweight stand-in for the JPA Criteria {@code Root} +
 * {@code CriteriaBuilder}.
 *
 * @param <T> the entity type (kept for type-level documentation; property names are strings)
 */
public interface Root<T> {

    /** {@code property = value}. */
    Condition equal(String property, Object value);

    /** {@code property = value}, case-insensitive. */
    Condition equalIgnoreCase(String property, Object value);

    /** {@code property <> value}. */
    Condition notEqual(String property, Object value);

    /** {@code property < value}. */
    Condition lessThan(String property, Object value);

    /** {@code property <= value}. */
    Condition lessThanOrEqual(String property, Object value);

    /** {@code property > value}. */
    Condition greaterThan(String property, Object value);

    /** {@code property >= value}. */
    Condition greaterThanOrEqual(String property, Object value);

    /** {@code property LIKE pattern} (the caller supplies the wildcards). */
    Condition like(String property, String pattern);

    /** {@code property LIKE %value%}. */
    Condition contains(String property, String value);

    /** {@code property LIKE value%}. */
    Condition startsWith(String property, String value);

    /** {@code property LIKE %value}. */
    Condition endsWith(String property, String value);

    /** {@code property IN (values...)}. */
    Condition in(String property, Collection<?> values);

    /** {@code property BETWEEN low AND high}. */
    Condition between(String property, Object low, Object high);

    /** {@code property IS NULL}. */
    Condition isNull(String property);

    /** {@code property IS NOT NULL}. */
    Condition isNotNull(String property);

    /** {@code property = TRUE}. */
    Condition isTrue(String property);

    /** {@code property = FALSE}. */
    Condition isFalse(String property);

    /** The physical column name for {@code property}; an escape hatch for building custom conditions. */
    String column(String property);
}
