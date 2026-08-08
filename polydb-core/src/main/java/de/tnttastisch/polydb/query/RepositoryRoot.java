package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Condition;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The {@link Root} implementation backing {@link Specification} execution. It resolves each property to
 * its column and converts bound values through the owning {@link JdbcRepository} (enums by name,
 * owning relations by foreign-key id) before delegating to the {@link Condition} factories.
 *
 * @param <T> the entity type (matches the specification's type parameter)
 */
final class RepositoryRoot<T> implements Root<T> {

    private final JdbcRepository<?, ?> repository;

    RepositoryRoot(JdbcRepository<?, ?> repository) {
        this.repository = repository;
    }

    @Override
    public Condition equal(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.eq(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition equalIgnoreCase(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.eqIgnoreCase(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition notEqual(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.ne(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition lessThan(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.lt(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition lessThanOrEqual(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.lte(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition greaterThan(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.gt(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition greaterThanOrEqual(String property, Object value) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.gte(field.getColumnName(), repository.toColumnValue(field, value));
    }

    @Override
    public Condition like(String property, String pattern) {
        return Condition.like(column(property), pattern);
    }

    @Override
    public Condition contains(String property, String value) {
        return Condition.like(column(property), "%" + value + "%");
    }

    @Override
    public Condition startsWith(String property, String value) {
        return Condition.like(column(property), value + "%");
    }

    @Override
    public Condition endsWith(String property, String value) {
        return Condition.like(column(property), "%" + value);
    }

    @Override
    public Condition in(String property, Collection<?> values) {
        FieldModel field = repository.resolveProperty(property);
        List<Object> converted = new ArrayList<>();
        for (Object value : values) {
            converted.add(repository.toColumnValue(field, value));
        }
        return Condition.in(field.getColumnName(), converted);
    }

    @Override
    public Condition between(String property, Object low, Object high) {
        FieldModel field = repository.resolveProperty(property);
        return Condition.between(field.getColumnName(),
                repository.toColumnValue(field, low), repository.toColumnValue(field, high));
    }

    @Override
    public Condition isNull(String property) {
        return Condition.isNull(column(property));
    }

    @Override
    public Condition isNotNull(String property) {
        return Condition.isNotNull(column(property));
    }

    @Override
    public Condition isTrue(String property) {
        return Condition.eq(column(property), Boolean.TRUE);
    }

    @Override
    public Condition isFalse(String property) {
        return Condition.eq(column(property), Boolean.FALSE);
    }

    @Override
    public String column(String property) {
        return repository.resolveProperty(property).getColumnName();
    }
}
