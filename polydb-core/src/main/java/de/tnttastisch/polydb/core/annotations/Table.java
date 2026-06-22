package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the table (or collection) name an {@link Entity} maps to.
 *
 * <p>This annotation is optional. Without it the storage name is derived from the entity class
 * name; apply {@code @Table} when the mapped name must differ from that default.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

    /**
     * Explicit table/collection name. When empty ({@code ""}, the default) the name is derived
     * from the entity class name.
     */
    String name() default "";

}
