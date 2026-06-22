package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a many-to-many association realised through a join table.
 *
 * <p>The owning side ({@link #mappedBy()} empty) declares the {@link JoinTable}; the inverse side
 * sets {@link #mappedBy()} to the owning field's name and declares no join table.</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManyToMany {

    /**
     * Name of the owning field on the other side. Empty (default) means this side owns the
     * join table.
     */
    String mappedBy() default "";

    /**
     * When the associated collection is loaded. Defaults to {@link FetchType#LAZY}.
     */
    FetchType fetch() default FetchType.LAZY;

    /**
     * Operations cascaded from this entity to the associated entities. Empty (the default) means
     * no cascading. See {@link CascadeType}.
     */
    CascadeType[] cascade() default {};

    /**
     * The target entity type. When left at the default ({@code void.class}) it is derived from the
     * generic element type of the annotated collection.
     */
    Class<?> targetEntity() default void.class;
}
