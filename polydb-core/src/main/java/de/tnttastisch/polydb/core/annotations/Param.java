package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a method parameter so a {@link Query} can bind it by {@code :name}. Without it, a parameter
 * can still be bound positionally ({@code ?1}) or sequentially ({@code ?}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /** The bind name used as {@code :value} in the query. */
    String value();
}
