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

    FetchType fetch() default FetchType.LAZY;

    CascadeType[] cascade() default {};

    Class<?> targetEntity() default void.class;
}
