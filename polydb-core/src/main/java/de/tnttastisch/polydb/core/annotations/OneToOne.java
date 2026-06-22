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

    FetchType fetch() default FetchType.EAGER;

    boolean optional() default true;

    CascadeType[] cascade() default {};

    Class<?> targetEntity() default void.class;
}
