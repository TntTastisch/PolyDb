package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the field that holds the entity's primary key.
 *
 * <p>Every {@link Entity} must declare exactly one {@code @Id} field. The annotated column is used
 * to uniquely identify rows/documents and is the target of foreign keys created for relations
 * (see {@link JoinColumn}).</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Id {

    /**
     * Whether the database generates the key value automatically (e.g. an auto-increment /
     * identity column). Defaults to {@code false}, in which case the application is responsible
     * for assigning the identifier before persisting.
     */
    boolean autoIncrement() default false;

}
