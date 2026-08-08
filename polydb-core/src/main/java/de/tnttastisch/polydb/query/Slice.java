package de.tnttastisch.polydb.query;

import java.util.List;

/**
 * A sub-list of a larger result set that knows its position and whether more data follows, but
 * <em>not</em> the grand total (that would need an extra count query — see {@link Page} for the
 * counted variant). Useful for "load more"/infinite-scroll style paging.
 *
 * @param <T> the element type
 */
public interface Slice<T> {

    /** The rows in this slice. */
    List<T> getContent();

    /** The number of rows actually in this slice (may be less than the page size on the last page). */
    int getNumberOfElements();

    /** The zero-based index of this slice. */
    int getNumber();

    /** The requested page size. */
    int getSize();

    /** The sort that produced this slice. */
    Sort getSort();

    /** Whether a next slice exists. */
    boolean hasNext();

    /** Whether a previous slice exists. */
    boolean hasPrevious();

    /** Whether this is the first slice. */
    boolean isFirst();

    /** Whether this is the last slice. */
    boolean isLast();
}
