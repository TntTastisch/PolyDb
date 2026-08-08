package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a temporal field to be set to "now" automatically when the entity is first inserted, and left
 * untouched on later updates. Supported field types: {@code Instant}, {@code LocalDateTime},
 * {@code LocalDate}, {@code LocalTime}, {@code OffsetDateTime}, {@code ZonedDateTime} and {@code long}
 * (epoch milliseconds).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreatedDate {
}
