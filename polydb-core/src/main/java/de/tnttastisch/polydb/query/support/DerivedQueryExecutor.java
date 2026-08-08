package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.JdbcRepository;
import de.tnttastisch.polydb.query.Page;
import de.tnttastisch.polydb.query.PageImpl;
import de.tnttastisch.polydb.query.Pageable;
import de.tnttastisch.polydb.query.Slice;
import de.tnttastisch.polydb.query.SliceImpl;
import de.tnttastisch.polydb.query.Sort;
import de.tnttastisch.polydb.query.sql.Condition;
import de.tnttastisch.polydb.query.sql.Order;
import de.tnttastisch.polydb.schema.model.FieldModel;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes derived query methods against a backing {@link JdbcRepository}. It parses each method name
 * once (via {@link DerivedQuery}, cached per {@link Method}), resolves the predicate properties to
 * columns, binds the call arguments to the placeholders in method-name order, and runs the resulting
 * {@link Condition} through the repository's query primitives — dispatching on the parsed action and
 * the method's return type.
 *
 * <p>Supported return types: {@code find} → {@code List<T>}, {@code Optional<T>} or a single {@code T};
 * {@code count} → {@code long}/{@code int}; {@code exists} → {@code boolean}; {@code delete} →
 * {@code void} or the deleted-row count.</p>
 */
public final class DerivedQueryExecutor implements QueryMethodExecutor {

    private final JdbcRepository<?, ?> repository;
    private final AnnotationQueryExecutor annotationExecutor;
    private final Map<Method, DerivedQuery> cache = new ConcurrentHashMap<>();

    public DerivedQueryExecutor(JdbcRepository<?, ?> repository) {
        this.repository = repository;
        this.annotationExecutor = new AnnotationQueryExecutor(repository);
    }

    @Override
    public Object execute(Method method, Object[] args) {
        // A method carrying @Query runs its explicit SQL; everything else is derived from the name.
        if (AnnotationQueryExecutor.handles(method)) {
            return annotationExecutor.execute(method, args);
        }

        DerivedQuery query = cache.computeIfAbsent(method, m -> DerivedQuery.parse(m.getName()));
        Object[] arguments = args == null ? new Object[0] : args;

        int[] cursor = {0};
        Condition condition = buildCondition(method, query, arguments, cursor);

        // An optional trailing Pageable or Sort argument follows the predicate arguments.
        Pageable pageable = null;
        Sort sort = null;
        if (cursor[0] < arguments.length) {
            Object tail = arguments[cursor[0]];
            if (tail instanceof Pageable p) {
                pageable = p;
                cursor[0]++;
            } else if (tail instanceof Sort s) {
                sort = s;
                cursor[0]++;
            }
        }
        if (cursor[0] != arguments.length) {
            throw new PolyDBException("Query method " + method.getName() + " binds " + cursor[0]
                    + " argument(s) but was called with " + arguments.length);
        }

        return switch (query.getAction()) {
            case FIND -> executeFind(method, query, condition, sort, pageable);
            case COUNT -> toNumber(repository.countWhere(condition), method.getReturnType());
            case EXISTS -> repository.existsWhere(condition);
            case DELETE -> executeDelete(method, condition);
        };
    }

    private Condition buildCondition(Method method, DerivedQuery query, Object[] args, int[] cursor) {
        if (query.getOrGroups().isEmpty()) {
            return null;
        }
        List<Condition> orParts = new ArrayList<>();
        for (List<DerivedQuery.Predicate> group : query.getOrGroups()) {
            List<Condition> andParts = new ArrayList<>();
            for (DerivedQuery.Predicate predicate : group) {
                andParts.add(toCondition(method, predicate, args, cursor));
            }
            orParts.add(Condition.and(andParts));
        }
        return Condition.or(orParts);
    }

    private Condition toCondition(Method method, DerivedQuery.Predicate predicate, Object[] args, int[] cursor) {
        FieldModel field = repository.resolveProperty(predicate.property());
        String column = field.getColumnName();
        boolean ignoreCase = predicate.ignoreCase();

        return switch (predicate.keyword()) {
            case IS_NULL -> Condition.isNull(column);
            case IS_NOT_NULL -> Condition.isNotNull(column);
            case TRUE -> Condition.eq(column, Boolean.TRUE);
            case FALSE -> Condition.eq(column, Boolean.FALSE);
            case BETWEEN -> {
                Object low = convert(field, next(method, args, cursor));
                Object high = convert(field, next(method, args, cursor));
                yield Condition.between(column, low, high);
            }
            case IN -> Condition.in(column, convertCollection(field, next(method, args, cursor)));
            case NOT_IN -> Condition.notIn(column, convertCollection(field, next(method, args, cursor)));
            case STARTING_WITH -> like(column, asString(next(method, args, cursor)) + "%", ignoreCase);
            case ENDING_WITH -> like(column, "%" + asString(next(method, args, cursor)), ignoreCase);
            case CONTAINING -> like(column, "%" + asString(next(method, args, cursor)) + "%", ignoreCase);
            case LIKE -> like(column, asString(next(method, args, cursor)), ignoreCase);
            case NOT_LIKE -> Condition.notLike(column, asString(next(method, args, cursor)));
            case LESS_THAN, BEFORE -> Condition.lt(column, convert(field, next(method, args, cursor)));
            case LESS_THAN_EQUAL -> Condition.lte(column, convert(field, next(method, args, cursor)));
            case GREATER_THAN, AFTER -> Condition.gt(column, convert(field, next(method, args, cursor)));
            case GREATER_THAN_EQUAL -> Condition.gte(column, convert(field, next(method, args, cursor)));
            case NOT -> Condition.ne(column, convert(field, next(method, args, cursor)));
            case SIMPLE -> {
                Object value = convert(field, next(method, args, cursor));
                yield ignoreCase ? Condition.eqIgnoreCase(column, value) : Condition.eq(column, value);
            }
        };
    }

    private Object executeFind(Method method, DerivedQuery query, Condition condition, Sort sort, Pageable pageable) {
        List<Order> orders = orderItemsToOrders(query.getOrderItems());
        Sort extraSort = pageable != null ? pageable.getSort() : sort;
        if (extraSort != null) {
            orders.addAll(sortToOrders(extraSort));
        }
        Class<?> returnType = method.getReturnType();

        if (pageable != null) {
            return executePaged(method, condition, orders, pageable);
        }

        Long limit = query.getLimit() == null ? null : query.getLimit().longValue();
        if (Optional.class.equals(returnType)) {
            List<?> result = repository.findWhere(condition, orders, 1L, null);
            return result.isEmpty() ? Optional.empty() : Optional.of(projectOne(elementType(method), result.get(0)));
        }
        if (Iterable.class.isAssignableFrom(returnType)) {
            return projectList(elementType(method), repository.findWhere(condition, orders, limit, null));
        }
        if (returnType.isAssignableFrom(repository.getEntityClass())
                || ProjectionSupport.isProjection(returnType, repository.getEntityClass())) {
            List<?> result = repository.findWhere(condition, orders, 1L, null);
            return result.isEmpty() ? null : projectOne(returnType, result.get(0));
        }
        throw new PolyDBException("Unsupported return type for query method " + method.getName() + ": " + returnType);
    }

    private Object executePaged(Method method, Condition condition, List<Order> orders, Pageable pageable) {
        Class<?> returnType = method.getReturnType();
        Class<?> element = elementType(method);
        // Paging needs a deterministic order; fall back to the primary key when none was requested.
        List<Order> effective = orders.isEmpty()
                ? new ArrayList<>(List.of(Order.asc(repository.getIdColumnName())))
                : orders;
        long pageSize = pageable.getPageSize();
        long offset = pageable.getOffset();

        if (Page.class.isAssignableFrom(returnType)) {
            List<?> content = repository.findWhere(condition, effective, pageSize, offset);
            return new PageImpl<>(projectList(element, content), pageable, repository.countWhere(condition));
        }
        if (Slice.class.isAssignableFrom(returnType)) {
            // Fetch one extra row to learn whether a further slice exists, sparing the COUNT query.
            List<?> rows = repository.findWhere(condition, effective, pageSize + 1, offset);
            boolean hasNext = rows.size() > pageable.getPageSize();
            List<?> content = hasNext ? rows.subList(0, pageable.getPageSize()) : rows;
            return new SliceImpl<>(projectList(element, content), pageable, hasNext);
        }
        if (Iterable.class.isAssignableFrom(returnType)) {
            return projectList(element, repository.findWhere(condition, effective, pageSize, offset));
        }
        throw new PolyDBException("Unsupported paged return type for query method " + method.getName() + ": " + returnType);
    }

    /** Maps entities to projections when {@code element} is a projection type; otherwise returns them as-is. */
    private List<?> projectList(Class<?> element, List<?> entities) {
        if (!ProjectionSupport.isProjection(element, repository.getEntityClass())) {
            return entities;
        }
        List<Object> projected = new ArrayList<>(entities.size());
        for (Object entity : entities) {
            projected.add(ProjectionSupport.project(element, ProjectionSupport.entityAccessor(entity)));
        }
        return projected;
    }

    private Object projectOne(Class<?> target, Object entity) {
        if (!ProjectionSupport.isProjection(target, repository.getEntityClass())) {
            return entity;
        }
        return ProjectionSupport.project(target, ProjectionSupport.entityAccessor(entity));
    }

    /** The element type of a parameterised return ({@code List<X>}/{@code Optional<X>}/{@code Page<X>}). */
    private static Class<?> elementType(Method method) {
        Type generic = method.getGenericReturnType();
        if (generic instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof Class<?> clazz) {
                return clazz;
            }
            if (argument instanceof ParameterizedType nested) {
                return (Class<?>) nested.getRawType();
            }
        }
        return Object.class;
    }

    private Object executeDelete(Method method, Condition condition) {
        long deleted = repository.deleteWhere(condition);
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        return toNumber(deleted, returnType);
    }

    private List<Order> orderItemsToOrders(List<DerivedQuery.OrderItem> orderItems) {
        List<Order> orders = new ArrayList<>();
        for (DerivedQuery.OrderItem item : orderItems) {
            orders.add(new Order(repository.resolveProperty(item.property()).getColumnName(), item.direction()));
        }
        return orders;
    }

    private List<Order> sortToOrders(Sort sort) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrders()) {
            FieldModel field = repository.resolveProperty(order.getProperty());
            orders.add(new Order(field.getColumnName(), order.getDirection(), order.isIgnoreCase()));
        }
        return orders;
    }

    private Condition like(String column, String pattern, boolean ignoreCase) {
        return ignoreCase ? Condition.likeIgnoreCase(column, pattern) : Condition.like(column, pattern);
    }

    private Object convert(FieldModel field, Object value) {
        return repository.toColumnValue(field, value);
    }

    private List<Object> convertCollection(FieldModel field, Object value) {
        List<Object> converted = new ArrayList<>();
        for (Object element : toIterable(value)) {
            converted.add(convert(field, element));
        }
        return converted;
    }

    private Iterable<?> toIterable(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        throw new PolyDBException("An In/NotIn query argument must be a Collection or array, got: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private Object next(Method method, Object[] args, int[] cursor) {
        if (cursor[0] >= args.length) {
            throw new PolyDBException("Query method " + method.getName() + " needs more arguments than were provided");
        }
        return args[cursor[0]++];
    }

    private static String asString(Object value) {
        return String.valueOf(value);
    }

    private static Object toNumber(long value, Class<?> returnType) {
        if (returnType == int.class || returnType == Integer.class) {
            return (int) value;
        }
        return value;
    }
}
