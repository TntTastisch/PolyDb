package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a unique constraint to the annotated field's column.
 *
 * <p>Use this for a single-column uniqueness guarantee. For a composite unique constraint spanning
 * several columns, use a type-level {@link Index} with {@link Index#unique()} set to {@code true}
 * instead.</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Unique {

    /**
     * Explicit constraint name. When empty ({@code ""}, the default) a name is generated from the
     * table and column.
     */
    String name() default "";

}
