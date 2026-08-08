package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a repository method to an explicit SQL statement instead of deriving it from the method name.
 * The statement is <strong>native SQL</strong> for the active dialect (PolyDB executes SQL directly;
 * there is no JPQL layer), so table and column names are the physical database names.
 *
 * <p>Parameters may be bound three ways, matching the {@code ?} placeholders in the SQL left to right:
 * named ({@code :name}, paired with a method parameter marked {@link Param}), positional by index
 * ({@code ?1}, {@code ?2}, one-based over the method arguments), or plain sequential ({@code ?}). A
 * PostgreSQL-style {@code ::} cast is left untouched.</p>
 *
 * <p>A read query maps its rows to the method's return type — the entity (as {@code List<T>},
 * {@code Optional<T>} or a single {@code T}), a scalar such as {@code long}/{@code String} (first
 * column), or a {@code List} of scalars. A writing statement must additionally be marked
 * {@link Modifying}.</p>
 *
 * <pre>{@code
 * @Query("SELECT * FROM users WHERE age > :min ORDER BY age")
 * List<User> olderThan(@Param("min") int min);
 *
 * @Query("SELECT COUNT(*) FROM users WHERE enabled = true")
 * long countEnabled();
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {

    /** The native SQL statement. */
    String value();

    /**
     * Whether the statement is native SQL. PolyDB only supports native SQL, so this defaults to (and
     * must remain) {@code true}; it exists for familiarity and forward compatibility.
     */
    boolean nativeQuery() default true;
}
