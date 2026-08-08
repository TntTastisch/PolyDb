package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be set to the current auditor on insert (and left untouched afterwards). The
 * auditor is supplied by {@link de.tnttastisch.polydb.query.AuditingContext}; if none is configured
 * the field is left as-is.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreatedBy {
}
