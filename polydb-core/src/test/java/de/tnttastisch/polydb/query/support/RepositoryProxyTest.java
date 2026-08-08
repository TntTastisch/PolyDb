package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.DeepWidgetRepository;
import de.tnttastisch.polydb.testrepositories.WidgetRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage of the dynamic-proxy repository path ({@code PolyDB.getRepository(...)}): a
 * user-declared interface gets a working implementation whose CRUD calls hit the database, whose
 * {@code default} methods run their own body (and can call CRUD through the proxy), whose
 * {@link Object} methods behave, and whose not-yet-supported query methods fail loudly.
 */
class RepositoryProxyTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:proxy_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    private static Widget widget(String name) {
        Widget widget = new Widget(UUID.randomUUID(), name, 1);
        widget.setActive(true);
        return widget;
    }

    @Test
    void proxyDelegatesCrudToDatabase() {
        try (PolyDB db = start()) {
            WidgetRepository widgets = db.getRepository(WidgetRepository.class);
            Widget saved = widgets.save(widget("proxied"));

            assertThat(widgets.findById(saved.getId())).isPresent();
            assertThat(widgets.count()).isEqualTo(1);
        }
    }

    @Test
    void defaultMethodRunsAndComposesCrud() {
        try (PolyDB db = start()) {
            WidgetRepository widgets = db.getRepository(WidgetRepository.class);
            assertThat(widgets.isEmpty()).isTrue();

            widgets.save(widget("one"));
            assertThat(widgets.isEmpty()).isFalse();
        }
    }

    @Test
    void unsupportedQueryMethodThrows() {
        try (PolyDB db = start()) {
            WidgetRepository widgets = db.getRepository(WidgetRepository.class);
            assertThatThrownBy(() -> widgets.findByName("x"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("findByName");
        }
    }

    @Test
    void resolvesRepositoryThroughMultipleInheritanceLevels() {
        try (PolyDB db = start()) {
            DeepWidgetRepository widgets = db.getRepository(DeepWidgetRepository.class);
            Widget saved = widgets.save(widget("deep"));
            assertThat(widgets.existsById(saved.getId())).isTrue();
        }
    }

    @Test
    void objectMethodsBehaveOnProxy() {
        try (PolyDB db = start()) {
            WidgetRepository widgets = db.getRepository(WidgetRepository.class);

            assertThat(widgets).isEqualTo(widgets);
            assertThat(widgets.equals(db.getRepository(WidgetRepository.class))).isFalse();
            assertThat(widgets.hashCode()).isEqualTo(widgets.hashCode());
            assertThat(widgets.toString()).contains("WidgetRepository");
        }
    }
}
