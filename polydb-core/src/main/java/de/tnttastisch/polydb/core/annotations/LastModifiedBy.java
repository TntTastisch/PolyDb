package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be set to the current auditor on every insert and update. The auditor is supplied
 * by {@link de.tnttastisch.polydb.query.AuditingContext}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LastModifiedBy {
}
