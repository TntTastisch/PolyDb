package de.tnttastisch.polydb.query;

import java.util.Objects;

/**
 * The standard {@link Pageable} implementation: a zero-based page index, a page size, and a
 * {@link Sort}. Create one with {@link #of(int, int)} or {@link #of(int, int, Sort)}.
 */
public final class PageRequest implements Pageable {

    private final int pageNumber;
    private final int pageSize;
    private final Sort sort;

    private PageRequest(int pageNumber, int pageSize, Sort sort) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least one");
        }
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.sort = Objects.requireNonNull(sort, "sort must not be null");
    }

    /** An unsorted page request. */
    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize, Sort.unsorted());
    }

    /** A sorted page request. */
    public static PageRequest of(int pageNumber, int pageSize, Sort sort) {
        return new PageRequest(pageNumber, pageSize, sort);
    }

    @Override
    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public int getPageSize() {
        return pageSize;
    }

    @Override
    public long getOffset() {
        return (long) pageNumber * pageSize;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new PageRequest(pageNumber + 1, pageSize, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return pageNumber == 0 ? this : new PageRequest(pageNumber - 1, pageSize, sort);
    }

    @Override
    public Pageable first() {
        return new PageRequest(0, pageSize, sort);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PageRequest other
                && pageNumber == other.pageNumber
                && pageSize == other.pageSize
                && sort.equals(other.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageNumber, pageSize, sort);
    }

    @Override
    public String toString() {
        return "PageRequest[page=" + pageNumber + ", size=" + pageSize + ", sort=" + sort + "]";
    }
}
