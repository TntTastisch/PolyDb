package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.query.sql.Direction;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.WidgetSpecRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of {@link Specification} filtering against H2: individual predicates, AND/OR/NOT
 * composition, {@code in}/{@code between}, and the sorted/paged/count/exists executor methods, over a
 * fixed four-row fixture.
 */
class SpecificationIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:spec_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
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

    private WidgetSpecRepository seeded(PolyDB db) {
        WidgetSpecRepository widgets = db.getRepository(WidgetSpecRepository.class);
        widgets.save(widget("alpha", 5, true, Widget.Status.ACTIVE));
        widgets.save(widget("beta", 10, true, Widget.Status.ACTIVE));
        widgets.save(widget("gamma", 15, false, Widget.Status.RETIRED));
        widgets.save(widget("delta", 20, true, Widget.Status.NEW));
        return widgets;
    }

    private static Specification<Widget> active(boolean active) {
        return root -> root.equal("active", active);
    }

    private static Specification<Widget> quantityAtLeast(int quantity) {
        return root -> root.greaterThanOrEqual("quantity", quantity);
    }

    private static Specification<Widget> status(Widget.Status status) {
        return root -> root.equal("status", status);
    }

    @Test
    void singlePredicate() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findAll(active(true))).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("alpha", "beta", "delta");
        }
    }

    @Test
    void andComposition() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findAll(active(true).and(quantityAtLeast(15))))
                    .extracting(Widget::getName).containsExactly("delta");
        }
    }

    @Test
    void orComposition() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findAll(status(Widget.Status.ACTIVE).or(status(Widget.Status.NEW))))
                    .extracting(Widget::getName).containsExactlyInAnyOrder("alpha", "beta", "delta");
        }
    }

    @Test
    void notComposition() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findAll(Specification.not(active(true))))
                    .extracting(Widget::getName).containsExactly("gamma");
        }
    }

    @Test
    void betweenAndIn() {
        try (PolyDB db = start()) {
            WidgetSpecRepository widgets = seeded(db);
            assertThat(widgets.count(root -> root.between("quantity", 5, 15))).isEqualTo(3);
            assertThat(widgets.findAll(root -> root.in("status", List.of(Widget.Status.ACTIVE, Widget.Status.NEW))))
                    .extracting(Widget::getName).containsExactlyInAnyOrder("alpha", "beta", "delta");
        }
    }

    @Test
    void existsAndFindOne() {
        try (PolyDB db = start()) {
            WidgetSpecRepository widgets = seeded(db);
            assertThat(widgets.exists(root -> root.contains("name", "lph"))).isTrue();
            assertThat(widgets.findOne(root -> root.equal("name", "delta"))).map(Widget::getName).contains("delta");
        }
    }

    @Test
    void sortedAndPaged() {
        try (PolyDB db = start()) {
            WidgetSpecRepository widgets = seeded(db);

            assertThat(widgets.findAll(active(true), Sort.by(Direction.DESC, "quantity")))
                    .extracting(Widget::getQuantity).containsExactly(20, 10, 5);

            Page<Widget> page = widgets.findAll(active(true), PageRequest.of(0, 2, Sort.by(Direction.DESC, "quantity")));
            assertThat(page.getContent()).extracting(Widget::getQuantity).containsExactly(20, 10);
            assertThat(page.getTotalElements()).isEqualTo(3);
        }
    }

    @Test
    void nullSpecificationMatchesEverything() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findAll((Specification<Widget>) null)).hasSize(4);
        }
    }
}
