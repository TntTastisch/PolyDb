package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a persistent entity managed by PolyDB.
 *
 * <p>An entity is the root unit of persistence: each instance is mapped to a row (SQL) or a
 * document (NoSQL), and exactly one of its fields must be annotated with {@link Id}. The mapped
 * table/collection name defaults to a name derived from the class and can be overridden with
 * {@link Table}. Only types carrying this annotation are scanned for {@link Column} and relation
 * mappings.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
}
