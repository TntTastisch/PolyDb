package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.annotations.Modifying;
import de.tnttastisch.polydb.core.annotations.Param;
import de.tnttastisch.polydb.core.annotations.Query;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.JdbcRepository;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes {@link Query}-annotated repository methods against native SQL. It rewrites the statement's
 * bind markers ({@code :name}, {@code ?1}, {@code ?}) into positional {@code ?} placeholders once per
 * method (cached), binds the call arguments in placeholder order, and dispatches on {@link Modifying}
 * and the method's return type: writing statements return the affected-row count (or {@code void});
 * reads map to the entity ({@code List<T>}/{@code Optional<T>}/{@code T}), a scalar, or a list of
 * scalars.
 */
public final class AnnotationQueryExecutor implements QueryMethodExecutor {

    private final JdbcRepository<?, ?> repository;
    private final Map<Method, ParsedQuery> cache = new ConcurrentHashMap<>();

    public AnnotationQueryExecutor(JdbcRepository<?, ?> repository) {
        this.repository = repository;
    }

    /** Whether this executor handles {@code method} (i.e. it carries a {@link Query}). */
    public static boolean handles(Method method) {
        return method.isAnnotationPresent(Query.class);
    }

    @Override
    public Object execute(Method method, Object[] args) {
        ParsedQuery parsed = cache.computeIfAbsent(method, AnnotationQueryExecutor::parse);
        List<Object> params = parsed.bind(method, args);

        if (method.isAnnotationPresent(Modifying.class)) {
            int affected = repository.executeUpdate(parsed.sql(), params);
            return toUpdateReturn(affected, method.getReturnType());
        }
        return mapSelect(method, parsed.sql(), params);
    }

    private Object mapSelect(Method method, String sql, List<Object> params) {
        Class<?> returnType = method.getReturnType();

        if (Optional.class.equals(returnType)) {
            Class<?> element = genericArgument(method);
            if (isEntity(element)) {
                List<?> rows = repository.query(sql, params);
                return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
            }
            List<Object> column = repository.queryScalarColumn(sql, params);
            return column.isEmpty() ? Optional.empty() : Optional.ofNullable(coerceScalar(column.get(0), element));
        }

        if (Iterable.class.isAssignableFrom(returnType)) {
            Class<?> element = genericArgument(method);
            if (isEntity(element)) {
                return repository.query(sql, params);
            }
            List<Object> converted = new ArrayList<>();
            for (Object value : repository.queryScalarColumn(sql, params)) {
                converted.add(coerceScalar(value, element));
            }
            return converted;
        }

        if (isEntity(returnType)) {
            List<?> rows = repository.query(sql, params);
            return rows.isEmpty() ? null : rows.get(0);
        }

        // Scalar single value (e.g. long from COUNT).
        List<Object> column = repository.queryScalarColumn(sql, params);
        return coerceScalar(column.isEmpty() ? null : column.get(0), returnType);
    }

    private boolean isEntity(Class<?> type) {
        return repository.getEntityClass().equals(type);
    }

    /** The element type of a parameterised return ({@code List<X>}/{@code Optional<X>}), or Object. */
    private static Class<?> genericArgument(Method method) {
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

    private static Object toUpdateReturn(int affected, Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == long.class || returnType == Long.class) {
            return (long) affected;
        }
        return affected;
    }

    // ------------------------------------------------------------------ SQL parsing / binding

    private static ParsedQuery parse(Method method) {
        Query query = method.getAnnotation(Query.class);
        if (!query.nativeQuery()) {
            throw new PolyDBException("@Query on " + method.getName()
                    + " has nativeQuery=false, but PolyDB only supports native SQL");
        }
        String sql = query.value();

        StringBuilder rewritten = new StringBuilder(sql.length());
        List<ParamRef> refs = new ArrayList<>();
        int sequential = 0;
        boolean inString = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inString = !inString;
                rewritten.append(c);
                continue;
            }
            if (inString) {
                rewritten.append(c);
                continue;
            }
            if (c == ':') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == ':') {
                    rewritten.append("::"); // PostgreSQL cast, not a bind marker
                    i++;
                    continue;
                }
                int end = i + 1;
                while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
                    end++;
                }
                if (end > i + 1) {
                    refs.add(ParamRef.named(sql.substring(i + 1, end)));
                    rewritten.append('?');
                    i = end - 1;
                    continue;
                }
                rewritten.append(c);
                continue;
            }
            if (c == '?') {
                int end = i + 1;
                while (end < sql.length() && Character.isDigit(sql.charAt(end))) {
                    end++;
                }
                if (end > i + 1) {
                    refs.add(ParamRef.indexed(Integer.parseInt(sql.substring(i + 1, end))));
                    i = end - 1;
                } else {
                    refs.add(ParamRef.sequential(sequential++));
                }
                rewritten.append('?');
                continue;
            }
            rewritten.append(c);
        }
        return new ParsedQuery(rewritten.toString(), refs, namedParameters(method));
    }

    private static Map<String, Integer> namedParameters(Method method) {
        Map<String, Integer> names = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Param param = parameters[i].getAnnotation(Param.class);
            if (param != null) {
                names.put(param.value(), i);
            }
        }
        return names;
    }

    private static Object bindValue(Object value) {
        // Enums are stored by name, so bind their name (matching the rest of the mapping layer).
        return value instanceof Enum<?> e ? e.name() : value;
    }

    private static Object coerceScalar(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (target == long.class || target == Long.class) return number.longValue();
            if (target == int.class || target == Integer.class) return number.intValue();
            if (target == double.class || target == Double.class) return number.doubleValue();
            if (target == float.class || target == Float.class) return number.floatValue();
            if (target == short.class || target == Short.class) return number.shortValue();
            if (target == boolean.class || target == Boolean.class) return number.intValue() != 0;
            if (target == BigDecimal.class) return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
            if (target == BigInteger.class) return number instanceof BigInteger bi ? bi : new BigDecimal(number.toString()).toBigInteger();
        }
        if (value instanceof String string) {
            if (target == UUID.class) return UUID.fromString(string);
            if (target == boolean.class || target == Boolean.class) return Boolean.parseBoolean(string);
        }
        if (target == String.class) {
            return value.toString();
        }
        return value;
    }

    /** A parsed statement: the rewritten SQL, its ordered bind references, and the name→index map. */
    private record ParsedQuery(String sql, List<ParamRef> refs, Map<String, Integer> namedIndexes) {

        List<Object> bind(Method method, Object[] args) {
            Object[] arguments = args == null ? new Object[0] : args;
            List<Object> values = new ArrayList<>(refs.size());
            for (ParamRef ref : refs) {
                values.add(bindValue(ref.resolve(method, arguments, namedIndexes)));
            }
            return values;
        }
    }

    /** One bind marker's source: a named parameter, a one-based index, or the next sequential argument. */
    private record ParamRef(String name, int index, Kind kind) {

        enum Kind { NAMED, INDEXED, SEQUENTIAL }

        static ParamRef named(String name) {
            return new ParamRef(name, -1, Kind.NAMED);
        }

        static ParamRef indexed(int oneBasedIndex) {
            return new ParamRef(null, oneBasedIndex, Kind.INDEXED);
        }

        static ParamRef sequential(int order) {
            return new ParamRef(null, order, Kind.SEQUENTIAL);
        }

        Object resolve(Method method, Object[] args, Map<String, Integer> namedIndexes) {
            int argIndex = switch (kind) {
                case NAMED -> {
                    Integer resolved = namedIndexes.get(name);
                    if (resolved == null) {
                        throw new PolyDBException("@Query on " + method.getName() + " references :" + name
                                + " but no parameter is annotated @Param(\"" + name + "\")");
                    }
                    yield resolved;
                }
                case INDEXED -> index - 1;
                case SEQUENTIAL -> index;
            };
            if (argIndex < 0 || argIndex >= args.length) {
                throw new PolyDBException("@Query on " + method.getName()
                        + " binds an argument out of range (index " + argIndex + " of " + args.length + ")");
            }
            return args[argIndex];
        }
    }
}
