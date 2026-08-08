package de.tnttastisch.polydb.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard {@link Page} implementation: wraps the page content together with the originating
 * {@link Pageable} and the total element count, deriving the total page count and the
 * first/last/has-next flags from them.
 *
 * @param <T> the element type
 */
public final class PageImpl<T> implements Page<T> {

    private final List<T> content;
    private final Pageable pageable;
    private final long total;

    /**
     * @param content  the rows on this page
     * @param pageable the request that produced the page
     * @param total    the total number of matching rows across all pages
     */
    public PageImpl(List<T> content, Pageable pageable, long total) {
        this.content = Collections.unmodifiableList(new ArrayList<>(content));
        this.pageable = pageable;
        // Guard against a total that is inconsistent with the content actually returned.
        this.total = Math.max(total, pageable.getOffset() + content.size());
    }

    @Override
    public List<T> getContent() {
        return content;
    }

    @Override
    public int getNumberOfElements() {
        return content.size();
    }

    @Override
    public int getNumber() {
        return pageable.getPageNumber();
    }

    @Override
    public int getSize() {
        return pageable.getPageSize();
    }

    @Override
    public Sort getSort() {
        return pageable.getSort();
    }

    @Override
    public long getTotalElements() {
        return total;
    }

    @Override
    public int getTotalPages() {
        int size = getSize();
        return size == 0 ? 1 : (int) Math.ceil((double) total / (double) size);
    }

    @Override
    public boolean hasNext() {
        return getNumber() + 1 < getTotalPages();
    }

    @Override
    public boolean hasPrevious() {
        return getNumber() > 0;
    }

    @Override
    public boolean isFirst() {
        return !hasPrevious();
    }

    @Override
    public boolean isLast() {
        return !hasNext();
    }

    @Override
    public String toString() {
        return "Page " + (getNumber() + 1) + " of " + getTotalPages()
                + " (total " + total + " elements, " + getNumberOfElements() + " on this page)";
    }
}
