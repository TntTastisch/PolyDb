package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.parser.EntityParser;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps a {@link ResultSet} row onto an entity instance. Only scalar columns are mapped here;
 * foreign-key (relation) columns are skipped and the corresponding association fields are populated
 * separately by the repository's relation-loading logic.
 */
public class DefaultResultMapper<T> implements ResultMapper<T> {

    private final Class<T> clazz;
    private final EntityModel model;

    public DefaultResultMapper(Class<T> clazz) {
        this(clazz, new EntityParser().parseEntity(clazz));
    }

    public DefaultResultMapper(Class<T> clazz, EntityModel model) {
        this.clazz = clazz;
        this.model = model;
    }

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
    private Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }

        if (value instanceof java.sql.Timestamp ts) {
            if (target == LocalDateTime.class) return ts.toLocalDateTime();
            if (target == OffsetDateTime.class) return ts.toInstant().atOffset(ZoneOffset.UTC);
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
        }

        if (value instanceof String string) {
            if (target == UUID.class) return UUID.fromString(string);
            if (target == Boolean.class || target == boolean.class) return Boolean.parseBoolean(string);
        }

        if (target == String.class) {
            return value.toString();
        }

        return value;
    }
}
