package de.tnttastisch.polydb.query;

/**
 * Abstract, zero-based pagination request: which page (0-indexed), how many rows per page, and the
 * {@link Sort} to apply. The concrete implementation is {@link PageRequest}. Model of Spring Data's
 * {@code Pageable}, trimmed to the essentials PolyDB needs.
 */
public interface Pageable {

    /** The zero-based page index. */
    int getPageNumber();

    /** The number of rows per page. */
    int getPageSize();

    /** The row offset of this page, i.e. {@code pageNumber * pageSize}. */
    long getOffset();

    /** The ordering to apply; {@link Sort#unsorted()} when none. */
    Sort getSort();

    /** The request for the next page. */
    Pageable next();

    /** The request for the previous page, or this request when already on the first page. */
    Pageable previousOrFirst();

    /** The request for the first page. */
    Pageable first();
}
