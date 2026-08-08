package de.tnttastisch.polydb.query;

/**
 * A {@link Slice} that also knows the total number of matching rows across all pages (and therefore
 * the total page count). Obtaining these totals costs an extra {@code COUNT} query compared to a bare
 * {@link Slice}. Model of Spring Data's {@code Page}.
 *
 * @param <T> the element type
 */
public interface Page<T> extends Slice<T> {

    /** The total number of matching rows across all pages. */
    long getTotalElements();

    /** The total number of pages. */
    int getTotalPages();
}
