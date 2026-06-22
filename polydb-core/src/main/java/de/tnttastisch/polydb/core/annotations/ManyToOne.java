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

    /**
     * When the associated entity is loaded. Defaults to {@link FetchType#EAGER}, so the target is
     * resolved as part of the owning query.
     */
    FetchType fetch() default FetchType.EAGER;

    /**
     * Whether the association (and therefore the foreign-key column) may be {@code null}. Defaults
     * to {@code true}; {@code false} produces a {@code NOT NULL} foreign-key column.
     */
    boolean optional() default true;

    /**
     * Operations cascaded from this entity to the associated entity. Empty (the default) means no
     * cascading. See {@link CascadeType}.
     */
    CascadeType[] cascade() default {};
}
