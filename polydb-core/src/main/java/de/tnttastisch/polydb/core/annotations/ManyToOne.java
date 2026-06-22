package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a many-to-one association. The annotated field is the <em>owning</em> side and is backed
 * by a foreign-key column on this entity's table (see {@link JoinColumn}).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManyToOne {

    /**
     * The target entity type. When left at the default ({@code void.class}) it is derived from the
     * declared field type.
     */
    Class<?> targetEntity() default void.class;

    FetchType fetch() default FetchType.EAGER;

    /**
     * Whether the association (and therefore the foreign-key column) may be {@code null}.
     */
    boolean optional() default true;

    CascadeType[] cascade() default {};
}
