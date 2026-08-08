package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link Query} method as a writing statement ({@code UPDATE}, {@code DELETE} or
 * {@code INSERT}) rather than a read. Such a method returns the affected-row count ({@code int} or
 * {@code long}) or {@code void}.
 *
 * <p>Each call runs on its own auto-committed connection for now; wrapping several writes in one
 * transaction arrives with the transaction support.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Modifying {
}
