package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a one-to-one association.
 *
 * <p>When {@link #mappedBy()} is empty this is the <em>owning</em> side and a foreign-key column is
 * created on this entity's table (see {@link JoinColumn}). When {@link #mappedBy()} is set this is
 * the inverse side and no column is created here.</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToOne {

    /**
     * Name of the owning field on the other side. Empty (default) means this side owns the
     * foreign key.
     */
    String mappedBy() default "";

    /**
     * When the associated entity is loaded. Defaults to {@link FetchType#EAGER}.
     */
    FetchType fetch() default FetchType.EAGER;

    /**
     * Whether the association may be {@code null}. Defaults to {@code true}. On the owning side
     * {@code false} produces a {@code NOT NULL} foreign-key column.
     */
    boolean optional() default true;

    /**
     * Operations cascaded from this entity to the associated entity. Empty (the default) means no
     * cascading. See {@link CascadeType}.
     */
    CascadeType[] cascade() default {};

    /**
     * The target entity type. When left at the default ({@code void.class}) it is derived from the
     * declared field type.
     */
    Class<?> targetEntity() default void.class;
}
