package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures the foreign-key column for an owning {@link ManyToOne} or {@link OneToOne} association.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JoinColumn {

    /**
     * Foreign-key column name. Defaults to {@code <fieldName>_id}.
     */
    String name() default "";

    /**
     * Referenced column on the target table. Defaults to the target entity's primary-key column.
     */
    String referencedColumnName() default "";

    boolean nullable() default true;
}
