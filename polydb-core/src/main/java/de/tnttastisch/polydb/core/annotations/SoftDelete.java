package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code boolean} flag field as the soft-delete marker. When present, {@code delete} sets the
 * flag to {@code true} instead of removing the row, and every repository read filters out rows whose
 * flag is {@code true}, so soft-deleted entities become invisible without leaving the table.
 *
 * <p>The flag models "deleted" (true = removed). Cascades are not applied to a soft delete.</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoftDelete {
}
