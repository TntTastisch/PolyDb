package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a one-to-many association. This is always the <em>inverse</em> (non-owning) side: the
 * foreign key lives on the target entity's table. No column is created on this entity's table.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneToMany {

    /**
     * Required. The name of the field on the target entity that owns the foreign key
     * (a {@link ManyToOne} or owning {@link OneToOne} field).
     */
    String mappedBy();

    FetchType fetch() default FetchType.LAZY;

    CascadeType[] cascade() default {};

    /**
     * The target entity type. When left at the default ({@code void.class}) it is derived from the
     * generic element type of the annotated collection.
     */
    Class<?> targetEntity() default void.class;
}
