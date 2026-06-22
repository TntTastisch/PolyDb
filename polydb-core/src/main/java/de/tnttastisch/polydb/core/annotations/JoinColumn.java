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

    /**
     * Whether the foreign-key column may be {@code null}. Defaults to {@code true}. Note that the
     * association's own {@code optional} flag (e.g. {@link ManyToOne#optional()}) typically governs
     * nullability; this element lets the column be configured directly.
     */
    boolean nullable() default true;
}
