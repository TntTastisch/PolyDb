package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a numeric field ({@code int}/{@code long} or their wrappers) as the optimistic-locking version.
 * It is initialised to {@code 0} on insert; each update increments it and guards the {@code WHERE}
 * clause with the previous value, so a concurrent modification (0 rows updated) raises an
 * {@link de.tnttastisch.polydb.core.exception.OptimisticLockException}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Version {
}
