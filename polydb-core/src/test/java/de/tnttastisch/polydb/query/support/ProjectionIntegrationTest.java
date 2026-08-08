package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testprojections.WidgetSummary;
import de.tnttastisch.polydb.testprojections.WidgetView;
import de.tnttastisch.polydb.testrepositories.WidgetProjectionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of projections against H2: interface and record projections returned from both
 * derived query methods (mapped from the loaded entity) and {@code @Query} (mapped from selected
 * columns), over a fixed four-row fixture.
 */
class ProjectionIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:proj_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
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

    private WidgetProjectionRepository seeded(PolyDB db) {
        WidgetProjectionRepository widgets = db.getRepository(WidgetProjectionRepository.class);
        widgets.save(widget("alpha", 5, true, Widget.Status.ACTIVE));
        widgets.save(widget("beta", 10, true, Widget.Status.ACTIVE));
        widgets.save(widget("gamma", 15, false, Widget.Status.RETIRED));
        widgets.save(widget("delta", 20, true, Widget.Status.NEW));
        return widgets;
    }

    @Test
    void derivedInterfaceProjection() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findByActiveTrue())
                    .extracting(WidgetView::getName).containsExactlyInAnyOrder("alpha", "beta", "delta");
        }
    }

    @Test
    void derivedInterfaceProjectionReadsEnum() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findByActiveTrue())
                    .allSatisfy(view -> assertThat(view.getStatus()).isNotNull());
        }
    }

    @Test
    void derivedRecordProjection() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).findByStatus(Widget.Status.ACTIVE))
                    .containsExactlyInAnyOrder(new WidgetSummary("alpha", 5), new WidgetSummary("beta", 10));
        }
    }

    @Test
    void annotatedRecordProjectionFromSelectedColumns() {
        try (PolyDB db = start()) {
            assertThat(seeded(db).summariesHeavierThan(10))
                    .containsExactly(new WidgetSummary("gamma", 15), new WidgetSummary("delta", 20));
        }
    }

    @Test
    void annotatedInterfaceProjection() {
        try (PolyDB db = start()) {
            Optional<WidgetView> view = seeded(db).viewByName("beta");
            assertThat(view).isPresent();
            assertThat(view.get().getName()).isEqualTo("beta");
            assertThat(view.get().getQuantity()).isEqualTo(10);
            assertThat(view.get().getStatus()).isEqualTo(Widget.Status.ACTIVE);
        }
    }
}
