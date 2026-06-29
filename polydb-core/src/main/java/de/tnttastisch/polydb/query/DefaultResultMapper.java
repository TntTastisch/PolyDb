package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.UUID;

/**
 * Maps a {@link ResultSet} row onto an entity instance. Only scalar columns are mapped here;
 * foreign-key (relation) columns are skipped and the corresponding association fields are populated
 * separately by the repository's relation-loading logic.
 */
public class DefaultResultMapper<T> implements ResultMapper<T> {

    private final Class<T> clazz;
    private final EntityModel model;

    /**
     * Convenience constructor that parses the entity model from {@code clazz} on the fly. Prefer
     * {@link #DefaultResultMapper(Class, EntityModel)} when a parsed model is already available to
     * avoid re-parsing per mapper.
     *
     * @param clazz the entity type to map rows onto
     */
    public DefaultResultMapper(Class<T> clazz) {
        this(clazz, new EntityParser().parseEntity(clazz));
    }

    /**
     * @param clazz the entity type to map rows onto
     * @param model the pre-parsed schema model describing {@code clazz}'s columns
     */
    public DefaultResultMapper(Class<T> clazz, EntityModel model) {
        this.clazz = clazz;
        this.model = model;
    }

    /**
     * Instantiates the entity via its no-arg constructor and copies each scalar column from the
     * current row into the matching field, coercing JDBC values to the declared field type. Fields
     * are accessed reflectively (and forced accessible), so the entity needs no setters. Null columns
     * leave the field at its default value; foreign-key columns are skipped because associations are
     * populated separately by the repository.
     *
     * @param rs the result set, positioned on the row to map
     * @return the populated entity
     * @throws SQLException if instantiation or reflective field access fails, wrapped with the
     *                      entity class name for context
     */
    @Override
    public T map(ResultSet rs) throws SQLException {
        try {
            T entity = clazz.getDeclaredConstructor().newInstance();
            for (FieldModel fieldModel : model.getFields()) {
                if (fieldModel.isForeignKey()) {
                    // The association object is loaded by the repository, not from this column.
                    continue;
                }
                Field field = fieldModel.getField();
                field.setAccessible(true);
                Object value = rs.getObject(fieldModel.getColumnName());

                if (value != null) {
                    field.set(entity, coerce(value, field.getType()));
                }
            }
            return entity;
        } catch (Exception e) {
            throw new SQLException("Failed to map ResultSet to " + clazz.getName(), e);
        }
    }

    /**
     * Converts a JDBC value to the Java type declared on the entity field. Drivers may return
     * {@code java.sql.Timestamp}/{@code Date}, widened numerics, or {@code String}/{@code UUID}
     * depending on the column type; this normalises the common cases.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }

        // Enums are persisted by name (see JdbcRepository#valueForColumn); resolve back via valueOf.
        if (target.isEnum() && value instanceof String enumName) {
            return Enum.valueOf((Class<? extends Enum>) target, enumName);
        }

        if (value instanceof java.sql.Timestamp ts) {
            if (target == LocalDateTime.class) return ts.toLocalDateTime();
            if (target == OffsetDateTime.class) return ts.toInstant().atOffset(ZoneOffset.UTC);
            if (target == ZonedDateTime.class) return ts.toInstant().atZone(ZoneOffset.UTC);
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
            if (target == Byte.class || target == byte.class) return number.byteValue();
            if (target == Boolean.class || target == boolean.class) return number.intValue() != 0;
            if (target == BigDecimal.class) {
                // Build from the string form so large/high-precision values are not truncated
                // through double (which only has a 53-bit mantissa).
                return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
            }
            if (target == BigInteger.class) {
                // Route through BigDecimal so a fractional driver value (e.g. 42.0) parses cleanly.
                return number instanceof BigInteger bi ? bi : new BigDecimal(number.toString()).toBigInteger();
            }
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
