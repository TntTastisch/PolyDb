package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds projection instances — interfaces or records that expose a subset of an entity's data.
 * A projection is created from a property source (a {@link Function} from property name to value):
 * a record is instantiated through its canonical constructor, an interface is served by a dynamic
 * proxy whose getters read the source. Values are coerced to the target property type, so a row-based
 * source (raw JDBC values) works as well as an entity-based one.
 *
 * <p>Two sources are provided: {@link #entityAccessor(Object)} reads an entity's fields by property
 * name (used by derived query methods, whose projection property names match entity properties), and
 * {@link #rowAccessor(Map)} reads a case-insensitive column map (used by {@code @Query}, whose
 * projection property names match the selected column labels).</p>
 */
public final class ProjectionSupport {

    private ProjectionSupport() {
    }

    /** Whether {@code candidate} is a projection type: an interface or record other than the entity. */
    public static boolean isProjection(Class<?> candidate, Class<?> entityType) {
        return candidate != null && !candidate.equals(entityType) && (candidate.isInterface() || candidate.isRecord());
    }

    /** Builds a projection of {@code type} from a property source. */
    public static Object project(Class<?> type, Function<String, Object> source) {
        if (type.isRecord()) {
            return fromRecord(type, source);
        }
        if (type.isInterface()) {
            return fromInterface(type, source);
        }
        throw new PolyDBException("Unsupported projection type " + type.getName() + "; use an interface or a record");
    }

    /** A property source reading an entity's declared fields (through its class hierarchy) by name. */
    public static Function<String, Object> entityAccessor(Object entity) {
        return property -> readField(entity, property);
    }

    /** A property source reading a case-insensitive column map. */
    public static Function<String, Object> rowAccessor(Map<String, Object> row) {
        Map<String, Object> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(row);
        return caseInsensitive::get;
    }

    private static Object fromRecord(Class<?> type, Function<String, Object> source) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            args[i] = coerce(source.apply(components[i].getName()), components[i].getType());
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new PolyDBException("Could not instantiate record projection " + type.getName(), e);
        }
    }

    private static Object fromInterface(Class<?> type, Function<String, Object> source) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> type.getSimpleName() + "-projection";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
            }
            return coerce(source.apply(propertyName(method.getName())), method.getReturnType());
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /** Strips a {@code get}/{@code is} accessor prefix and decapitalises; a bare name is used as-is. */
    private static String propertyName(String methodName) {
        String name = methodName;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        } else {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static Object readField(Object entity, String property) {
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(property);
                field.setAccessible(true);
                return field.get(entity);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new PolyDBException("Could not read projection property '" + property + "'", e);
            }
        }
        throw new PolyDBException("Projection property '" + property + "' has no matching field on "
                + entity.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        if (target.isEnum() && value instanceof String name) {
            return Enum.valueOf((Class<? extends Enum>) target, name);
        }
        if (value instanceof java.sql.Timestamp ts) {
            if (target == LocalDateTime.class) return ts.toLocalDateTime();
            if (target == Instant.class) return ts.toInstant();
            if (target == LocalDate.class) return ts.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.sql.Date date && target == LocalDate.class) {
            return date.toLocalDate();
        }
        if (value instanceof java.sql.Time time && target == LocalTime.class) {
            return time.toLocalTime();
        }
        if (value instanceof Number number) {
            if (target == Long.class || target == long.class) return number.longValue();
            if (target == Integer.class || target == int.class) return number.intValue();
            if (target == Double.class || target == double.class) return number.doubleValue();
            if (target == Float.class || target == float.class) return number.floatValue();
            if (target == Short.class || target == short.class) return number.shortValue();
            if (target == Boolean.class || target == boolean.class) return number.intValue() != 0;
            if (target == BigDecimal.class) return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
            if (target == BigInteger.class) return number instanceof BigInteger bi ? bi : new BigDecimal(number.toString()).toBigInteger();
        }
        if (value instanceof String string) {
            if (target == UUID.class) return UUID.fromString(string);
            if (target == Boolean.class || target == boolean.class) return Boolean.parseBoolean(string);
            if ((target == Character.class || target == char.class) && !string.isEmpty()) return string.charAt(0);
        }
        if (target == String.class) {
            return value.toString();
        }
        return value;
    }
}
