package de.tnttastisch.polydb.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a field to a persistent column and overrides the inferred column metadata.
 *
 * <p>This annotation is optional: a field without {@code @Column} is still persisted using the
 * default conventions (column name derived from the field name, nullable, and a length of
 * {@code 255} for string types). Use it to customise the generated DDL &ndash; for example to
 * pin a column name, mark a column {@code NOT NULL}, or size a numeric/decimal type.</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {

    /**
     * Explicit column name. When left empty ({@code ""}, the default) the column name is derived
     * from the field name.
     */
    String name() default "";

    /**
     * Whether the column may hold {@code NULL}. Defaults to {@code true}; set to {@code false} to
     * emit a {@code NOT NULL} constraint.
     */
    boolean nullable() default true;

    /**
     * Maximum length for variable-length (string) columns. Defaults to {@code 255} and is ignored
     * for types that do not carry a length.
     */
    int length() default 255;

    /**
     * Total number of digits for decimal/numeric columns. {@code 0} (the default) means the
     * dialect's default precision is used.
     */
    int precision() default 0;

    /**
     * Number of digits to the right of the decimal point for decimal/numeric columns. {@code 0}
     * (the default) means the dialect's default scale is used.
     */
    int scale() default 0;

    /**
     * Explicit default value for the column, emitted verbatim as a SQL literal in the generated
     * {@code DEFAULT} clause. This <em>overrides</em> any value derived from the field initialiser.
     *
     * <p>When left empty ({@code ""}, the default), the {@code DEFAULT} clause is instead derived
     * automatically from the field's initialised value on a freshly constructed instance &ndash; for
     * example {@code private boolean notify = false} yields {@code DEFAULT false} and
     * {@code private String role = "user"} yields {@code DEFAULT 'user'}. Fields left at {@code null}
     * (and types with no obvious literal form, such as {@code UUID}/dates/collections) get no
     * {@code DEFAULT} clause; use this attribute to supply one for them.</p>
     *
     * <p>Set it explicitly when the literal differs from the field value or needs dialect-specific
     * syntax: use {@code "false"}/{@code "0"} for booleans/numbers, {@code "'active'"} (with the inner
     * quotes) for strings, and expressions such as {@code "CURRENT_TIMESTAMP"} for functions. The
     * string is inserted as-is, so it must be a valid SQL literal for the target dialect.</p>
     *
     * <p>Having a default &ndash; derived or explicit &ndash; is what makes it safe to add a
     * {@code NOT NULL} column to a table that already holds rows: the database backfills the existing
     * rows with it instead of rejecting the {@code ALTER} for containing nulls.</p>
     */
    String defaultValue() default "";

}
