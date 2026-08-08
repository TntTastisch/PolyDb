package de.tnttastisch.polydb.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard {@link Slice} implementation. Unlike {@link PageImpl} it carries no total count; the
 * {@code hasNext} flag is supplied directly (typically discovered by fetching one row beyond the page
 * size), which is why a slice avoids the extra {@code COUNT} query.
 *
 * @param <T> the element type
 */
public final class SliceImpl<T> implements Slice<T> {

    private final List<T> content;
    private final Pageable pageable;
    private final boolean hasNext;

    /**
     * @param content  the rows on this slice
     * @param pageable the request that produced the slice
     * @param hasNext  whether a further slice follows
     */
    public SliceImpl(List<T> content, Pageable pageable, boolean hasNext) {
        this.content = Collections.unmodifiableList(new ArrayList<>(content));
        this.pageable = pageable;
        this.hasNext = hasNext;
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
    public boolean hasNext() {
        return hasNext;
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
        return "Slice " + (getNumber() + 1) + " (" + getNumberOfElements() + " elements, hasNext=" + hasNext + ")";
    }
}
