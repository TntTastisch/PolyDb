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

}
