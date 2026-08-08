package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the standard {@link CrudRepository} surface against an in-memory H2 database:
 * upsert semantics of {@code save}, the bulk reads/writes ({@code saveAll}, {@code findAllById},
 * {@code count}, {@code existsById}) and the delete variants. Each test runs on its own uniquely named
 * database so the cases stay isolated.
 */
class CrudRepositoryIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:crud_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    private static Widget widget(String name, int quantity) {
        Widget widget = new Widget(UUID.randomUUID(), name, quantity);
        widget.setActive(true);
        widget.setStatus(Widget.Status.ACTIVE);
        widget.setPrice(new BigDecimal("9.99"));
        return widget;
    }

    @Test
    void saveInsertsAndFindByIdReadsBack() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget saved = widgets.save(widget("gizmo", 7));

            Optional<Widget> found = widgets.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("gizmo");
            assertThat(found.get().getQuantity()).isEqualTo(7);
            assertThat(found.get().getStatus()).isEqualTo(Widget.Status.ACTIVE);
            assertThat(found.get().getPrice()).isEqualByComparingTo("9.99");
        }
    }

    @Test
    void saveUpdatesExistingRowInPlace() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget widget = widgets.save(widget("old", 1));

            widget.setName("new");
            widget.setQuantity(42);
            widgets.save(widget);

            assertThat(widgets.count()).isEqualTo(1);
            Widget reread = widgets.findById(widget.getId()).orElseThrow();
            assertThat(reread.getName()).isEqualTo("new");
            assertThat(reread.getQuantity()).isEqualTo(42);
        }
    }

    @Test
    void saveAllFindAllAndCount() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            widgets.saveAll(List.of(widget("a", 1), widget("b", 2), widget("c", 3)));

            assertThat(widgets.findAll()).hasSize(3);
            assertThat(widgets.count()).isEqualTo(3);
        }
    }

    @Test
    void existsByIdReflectsPresence() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget saved = widgets.save(widget("present", 1));

            assertThat(widgets.existsById(saved.getId())).isTrue();
            assertThat(widgets.existsById(UUID.randomUUID())).isFalse();
        }
    }

    @Test
    void findAllByIdReturnsRequestedSubset() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget a = widgets.save(widget("a", 1));
            widgets.save(widget("b", 2));
            Widget c = widgets.save(widget("c", 3));

            List<Widget> found = widgets.findAllById(List.of(a.getId(), c.getId()));
            assertThat(found).extracting(Widget::getName).containsExactlyInAnyOrder("a", "c");
        }
    }

    @Test
    void findAllByIdWithEmptyInputIsEmpty() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            widgets.save(widget("a", 1));
            assertThat(widgets.findAllById(List.of())).isEmpty();
        }
    }

    @Test
    void deleteByIdRemovesRow() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget saved = widgets.save(widget("doomed", 1));

            widgets.deleteById(saved.getId());
            assertThat(widgets.findById(saved.getId())).isEmpty();
            assertThat(widgets.count()).isZero();
        }
    }

    @Test
    void deleteByEntityRemovesRow() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget saved = widgets.save(widget("doomed", 1));

            widgets.delete(saved);
            assertThat(widgets.existsById(saved.getId())).isFalse();
        }
    }

    @Test
    void deleteAllByIdRemovesOnlyNamedRows() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget a = widgets.save(widget("a", 1));
            Widget b = widgets.save(widget("b", 2));
            Widget c = widgets.save(widget("c", 3));

            widgets.deleteAllById(List.of(a.getId(), c.getId()));

            assertThat(widgets.findAll()).extracting(Widget::getId).containsExactly(b.getId());
        }
    }

    @Test
    void deleteAllEmptiesTheTable() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            widgets.saveAll(List.of(widget("a", 1), widget("b", 2)));

            widgets.deleteAll();
            assertThat(widgets.count()).isZero();
        }
    }

    @Test
    void deleteAllByEntitiesRemovesGivenRows() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            Widget a = widgets.save(widget("a", 1));
            Widget b = widgets.save(widget("b", 2));

            widgets.deleteAll(List.of(a, b));
            assertThat(widgets.count()).isZero();
        }
    }
}
