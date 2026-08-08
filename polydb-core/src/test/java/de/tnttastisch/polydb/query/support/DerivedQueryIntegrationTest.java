package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.WidgetQueryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the derived-query engine against H2: every operator, {@code And}/{@code Or}
 * composition, ordering, {@code Top} limiting and the {@code count}/{@code exists}/{@code delete}
 * actions are exercised through a declared {@link WidgetQueryRepository} over a fixed four-row fixture.
 */
class DerivedQueryIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:derived_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    private static Widget widget(String name, int quantity, boolean active, Widget.Status status, String price) {
        Widget widget = new Widget(UUID.randomUUID(), name, quantity);
        widget.setActive(active);
        widget.setStatus(status);
        widget.setPrice(price == null ? null : new BigDecimal(price));
        return widget;
    }

    /** Seeds four widgets: alpha/5/active/ACTIVE/10, beta/10/active/ACTIVE/null, gamma/15/inactive/RETIRED/30, delta/20/active/NEW/null. */
    private WidgetQueryRepository seeded(PolyDB db) {
        WidgetQueryRepository widgets = db.getRepository(WidgetQueryRepository.class);
        widgets.save(widget("alpha", 5, true, Widget.Status.ACTIVE, "10.00"));
        widgets.save(widget("beta", 10, true, Widget.Status.ACTIVE, null));
        widgets.save(widget("gamma", 15, false, Widget.Status.RETIRED, "30.00"));
        widgets.save(widget("delta", 20, true, Widget.Status.NEW, null));
        return widgets;
    }

    @Test
    void equalityAndIgnoreCase() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByName("alpha")).extracting(Widget::getName).containsExactly("alpha");
            assertThat(widgets.findByNameIgnoreCase("BETA")).map(Widget::getName).contains("beta");
        }
    }

    @Test
    void relationalAndRange() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByQuantityGreaterThan(10)).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("gamma", "delta");
            assertThat(widgets.findByQuantityGreaterThanEqual(10)).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("beta", "gamma", "delta");
            assertThat(widgets.findByQuantityBetween(5, 15)).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("alpha", "beta", "gamma");
        }
    }

    @Test
    void booleanAndEnum() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByActiveTrue()).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("alpha", "beta", "delta");
            assertThat(widgets.findByActiveFalse()).extracting(Widget::getName).containsExactly("gamma");
            assertThat(widgets.findByStatus(Widget.Status.ACTIVE)).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("alpha", "beta");
            assertThat(widgets.findByStatusIn(List.of(Widget.Status.ACTIVE, Widget.Status.NEW)))
                    .extracting(Widget::getName).containsExactlyInAnyOrder("alpha", "beta", "delta");
        }
    }

    @Test
    void stringMatching() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByNameContaining("lph")).extracting(Widget::getName).containsExactly("alpha");
            assertThat(widgets.findByNameStartingWith("del")).extracting(Widget::getName).containsExactly("delta");
            assertThat(widgets.findByNameEndingWith("ta")).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("beta", "delta");
        }
    }

    @Test
    void nullChecks() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByPriceIsNull()).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("beta", "delta");
            assertThat(widgets.findByPriceIsNotNull()).extracting(Widget::getName)
                    .containsExactlyInAnyOrder("alpha", "gamma");
        }
    }

    @Test
    void andOrComposition() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByNameAndQuantityGreaterThan("gamma", 10))
                    .extracting(Widget::getName).containsExactly("gamma");
            assertThat(widgets.findByStatusOrQuantityGreaterThan(Widget.Status.RETIRED, 18))
                    .extracting(Widget::getName).containsExactlyInAnyOrder("gamma", "delta");
        }
    }

    @Test
    void ordering() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findByActiveTrueOrderByQuantityDesc()).extracting(Widget::getQuantity)
                    .containsExactly(20, 10, 5);
            assertThat(widgets.findByActiveTrueOrderByQuantityAscNameDesc()).extracting(Widget::getQuantity)
                    .containsExactly(5, 10, 20);
        }
    }

    @Test
    void topAndFirstLimiting() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.findTop2ByActiveTrueOrderByQuantityDesc()).extracting(Widget::getQuantity)
                    .containsExactly(20, 10);
            assertThat(widgets.findFirstByOrderByQuantityDesc().getQuantity()).isEqualTo(20);
        }
    }

    @Test
    void countExistsDelete() {
        try (PolyDB db = start()) {
            WidgetQueryRepository widgets = seeded(db);
            assertThat(widgets.countByActiveTrue()).isEqualTo(3);
            assertThat(widgets.existsByName("beta")).isTrue();
            assertThat(widgets.existsByName("zzz")).isFalse();

            assertThat(widgets.deleteByStatus(Widget.Status.RETIRED)).isEqualTo(1);
            assertThat(widgets.count()).isEqualTo(3);
        }
    }
}
