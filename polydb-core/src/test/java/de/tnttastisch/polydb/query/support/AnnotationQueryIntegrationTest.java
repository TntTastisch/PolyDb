package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.WidgetSqlRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of {@code @Query}/{@code @Modifying} against H2: named/positional/sequential
 * parameter binding, entity/Optional/scalar/scalar-list reads, and modifying updates and deletes,
 * over a fixed four-row fixture.
 */
class AnnotationQueryIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:atq_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
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

    /** Seeds alpha/5/active/ACTIVE, beta/10/active/ACTIVE, gamma/15/inactive/RETIRED, delta/20/active/NEW. */
    private WidgetSqlRepository seeded(PolyDB db) {
        WidgetSqlRepository widgets = db.getRepository(WidgetSqlRepository.class);
        widgets.save(widget("alpha", 5, true, Widget.Status.ACTIVE));
        widgets.save(widget("beta", 10, true, Widget.Status.ACTIVE));
        widgets.save(widget("gamma", 15, false, Widget.Status.RETIRED));
        widgets.save(widget("delta", 20, true, Widget.Status.NEW));
        return widgets;
    }

    @Test
    void namedParameterEntityQuery() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).heavierThan(10)).extracting(Widget::getName).containsExactly("gamma", "delta");
        }
    }

    @Test
    void positionalParameterOptionalQuery() {
        try (PolyDB db = start()) {
            WidgetSqlRepository widgets = seeded(db);
            assertThat(widgets.byName("beta")).map(Widget::getName).contains("beta");
            assertThat(widgets.byName("nope")).isEmpty();
        }
    }

    @Test
    void scalarAggregateQuery() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).countActive()).isEqualTo(3);
        }
    }

    @Test
    void scalarListQuery() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).allNames()).containsExactly("alpha", "beta", "delta", "gamma");
        }
    }

    @Test
    void sequentialEnumParameterQuery() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).byStatus(Widget.Status.ACTIVE))
                    .extracting(Widget::getName).containsExactlyInAnyOrder("alpha", "beta");
        }
    }

    @Test
    void modifyingUpdateReturnsAffectedRows() {
        try (PolyDB db = start()) {
            WidgetSqlRepository widgets = seeded(db);
            assertThat(widgets.deactivateBelow(15)).isEqualTo(2); // alpha (5) and beta (10)
            assertThat(widgets.countActive()).isEqualTo(1);       // only delta remains active
        }
    }

    @Test
    void modifyingDeleteReturnsAffectedRows() {
        try (PolyDB db = start()) {
            WidgetSqlRepository widgets = seeded(db);
            assertThat(widgets.deleteInactive()).isEqualTo(1);    // gamma is the only inactive row
            assertThat(widgets.count()).isEqualTo(3);
        }
    }
}
