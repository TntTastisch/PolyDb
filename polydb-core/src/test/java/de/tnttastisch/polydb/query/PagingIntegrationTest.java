package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.query.sql.Direction;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.WidgetPagingRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of sorting and pagination against H2: the inherited
 * {@link PagingAndSortingRepository} methods and derived finders that take a trailing {@link Sort} or
 * {@link Pageable} and return a {@link Page}, {@link Slice} or {@link java.util.List}, over a fixed
 * five-row fixture.
 */
class PagingIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:paging_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    private static Widget widget(String name, int quantity, boolean active, Widget.Status status) {
        Widget widget = new Widget(UUID.randomUUID(), name, quantity);
        widget.setActive(active);
        widget.setStatus(status);
        return widget;
    }

    /** Seeds five widgets with distinct quantities 5/10/15/20/25. */
    private WidgetPagingRepository seeded(PolyDB db) {
        WidgetPagingRepository widgets = db.getRepository(WidgetPagingRepository.class);
        widgets.save(widget("alpha", 5, true, Widget.Status.ACTIVE));
        widgets.save(widget("beta", 10, true, Widget.Status.ACTIVE));
        widgets.save(widget("gamma", 15, false, Widget.Status.RETIRED));
        widgets.save(widget("delta", 20, true, Widget.Status.NEW));
        widgets.save(widget("epsilon", 25, true, Widget.Status.ACTIVE));
        return widgets;
    }

    @Test
    void findAllWithSort() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            assertThat(widgets.findAll(Sort.by(Direction.DESC, "quantity")))
                    .extracting(Widget::getQuantity).containsExactly(25, 20, 15, 10, 5);
        }
    }

    @Test
    void findAllWithPageable() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);

            Page<Widget> first = widgets.findAll(PageRequest.of(0, 2, Sort.by("quantity")));
            assertThat(first.getContent()).extracting(Widget::getQuantity).containsExactly(5, 10);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
            assertThat(first.isFirst()).isTrue();
            assertThat(first.hasNext()).isTrue();

            Page<Widget> last = widgets.findAll(PageRequest.of(2, 2, Sort.by("quantity")));
            assertThat(last.getContent()).extracting(Widget::getQuantity).containsExactly(25);
            assertThat(last.isLast()).isTrue();
            assertThat(last.hasNext()).isFalse();
        }
    }

    @Test
    void findAllWithPageableFallsBackToIdOrderWhenUnsorted() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            Page<Widget> page = widgets.findAll(PageRequest.of(0, 3));
            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getTotalElements()).isEqualTo(5);
        }
    }

    @Test
    void derivedQueryReturningPage() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            Page<Widget> page = widgets.findByActiveTrue(PageRequest.of(0, 2, Sort.by(Direction.DESC, "quantity")));
            assertThat(page.getContent()).extracting(Widget::getQuantity).containsExactly(25, 20);
            assertThat(page.getTotalElements()).isEqualTo(4); // four active widgets
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.hasNext()).isTrue();
        }
    }

    @Test
    void derivedQueryReturningSlice() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            Slice<Widget> firstSlice = widgets.findByStatus(Widget.Status.ACTIVE, PageRequest.of(0, 2));
            assertThat(firstSlice.getContent()).hasSize(2);
            assertThat(firstSlice.hasNext()).isTrue(); // three ACTIVE widgets in total

            Slice<Widget> secondSlice = widgets.findByStatus(Widget.Status.ACTIVE, PageRequest.of(1, 2));
            assertThat(secondSlice.getContent()).hasSize(1);
            assertThat(secondSlice.hasNext()).isFalse();
        }
    }

    @Test
    void derivedQueryReturningListWithPageable() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            assertThat(widgets.findByQuantityGreaterThan(10, PageRequest.of(0, 2, Sort.by(Direction.DESC, "quantity"))))
                    .extracting(Widget::getQuantity).containsExactly(25, 20);
        }
    }

    @Test
    void derivedQueryWithSortArgument() {
        try (PolyDB db = start()) {
            WidgetPagingRepository widgets = seeded(db);
            assertThat(widgets.findByActiveTrue(Sort.by(Direction.DESC, "quantity")))
                    .extracting(Widget::getQuantity).containsExactly(25, 20, 10, 5);
        }
    }
}
