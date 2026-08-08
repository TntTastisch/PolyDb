package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.query.sql.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the paging/sorting value types: {@link Sort} construction and combination,
 * {@link PageRequest} offset/navigation arithmetic, and {@link PageImpl} total/flag derivation.
 */
class PagingTypesTest {

    @Test
    void sortByPropertiesAscendingByDefault() {
        Sort sort = Sort.by("name", "createdAt");
        assertThat(sort.getOrders()).extracting(Sort.Order::getProperty).containsExactly("name", "createdAt");
        assertThat(sort.getOrders()).allMatch(o -> o.getDirection() == Direction.ASC);
        assertThat(sort.isSorted()).isTrue();
    }

    @Test
    void sortDirectionAndFlip() {
        assertThat(Sort.by(Direction.DESC, "name").getOrders().get(0).getDirection()).isEqualTo(Direction.DESC);
        Sort flipped = Sort.by("a", "b").descending();
        assertThat(flipped.getOrders()).allMatch(o -> o.getDirection() == Direction.DESC);
    }

    @Test
    void sortAndCombines() {
        Sort combined = Sort.by("a").and(Sort.by(Direction.DESC, "b"));
        assertThat(combined.getOrders()).extracting(Sort.Order::getProperty).containsExactly("a", "b");
        assertThat(combined.getOrders().get(1).getDirection()).isEqualTo(Direction.DESC);
    }

    @Test
    void unsortedIsEmpty() {
        assertThat(Sort.unsorted().isUnsorted()).isTrue();
        assertThat(Sort.unsorted().getOrders()).isEmpty();
    }

    @Test
    void pageRequestOffsetAndNavigation() {
        PageRequest request = PageRequest.of(2, 10);
        assertThat(request.getOffset()).isEqualTo(20);
        assertThat(request.next().getPageNumber()).isEqualTo(3);
        assertThat(request.previousOrFirst().getPageNumber()).isEqualTo(1);
        assertThat(request.first().getPageNumber()).isZero();
    }

    @Test
    void previousOrFirstStaysOnFirstPage() {
        assertThat(PageRequest.of(0, 10).previousOrFirst().getPageNumber()).isZero();
    }

    @Test
    void pageRequestRejectsInvalidArguments() {
        assertThatThrownBy(() -> PageRequest.of(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageRequest.of(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageImplDerivesTotalsAndFlagsForFirstPage() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getNumberOfElements()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.isLast()).isFalse();
    }

    @Test
    void pageImplDerivesFlagsForLastPage() {
        Page<String> page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.isFirst()).isFalse();
    }
}
