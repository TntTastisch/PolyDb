package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a database index.
 *
 * <p>May be applied either to a type or to a field. When placed on a {@link Entity} type it
 * defines a (possibly composite) index over the columns listed in {@link #columns()}. When placed
 * on a field it indexes that single column and {@link #columns()} can be left empty.</p>
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

    /**
     * Explicit index name. When empty ({@code ""}, the default) a name is generated from the
     * table and the indexed column(s).
     */
    String name() default "";

    /**
     * Columns covered by the index, in order. Required for a type-level (composite) index; may be
     * left empty ({@code {}}, the default) on a field-level index, where the annotated field's
     * column is used.
     */
    String[] columns() default {};

    /**
     * Whether the index enforces uniqueness across the covered columns. Defaults to {@code false}
     * (a plain, non-unique index).
     */
    boolean unique() default false;

}
