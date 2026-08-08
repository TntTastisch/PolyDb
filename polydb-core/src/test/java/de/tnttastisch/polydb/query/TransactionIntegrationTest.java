package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.testentities.Widget;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage of {@link TransactionTemplate} against H2: commit, rollback on exception, a
 * returned result, the read-only variant, and join-nesting (an inner template participates in the
 * outer transaction, so an outer failure rolls back the inner writes too).
 */
class TransactionIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:tx_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    private static Widget widget(String name) {
        return new Widget(UUID.randomUUID(), name, 1);
    }

    @Test
    void commitsAllWrites() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            tx.executeWithoutResult(() -> {
                widgets.save(widget("a"));
                widgets.save(widget("b"));
            });

            assertThat(widgets.count()).isEqualTo(2);
        }
    }

    @Test
    void rollsBackOnException() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            assertThatThrownBy(() -> tx.executeWithoutResult(() -> {
                widgets.save(widget("a"));
                throw new RuntimeException("boom");
            })).hasMessageContaining("boom");

            assertThat(widgets.count()).isZero();
        }
    }

    @Test
    void returnsResult() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            long count = tx.execute(() -> {
                widgets.save(widget("a"));
                return widgets.count();
            });

            assertThat(count).isEqualTo(1);
            assertThat(widgets.count()).isEqualTo(1);
        }
    }

    @Test
    void readOnlyTransactionReads() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            widgets.save(widget("a"));
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            assertThat(tx.executeReadOnly(widgets::count)).isEqualTo(1);
        }
    }

    @Test
    void nestedTransactionsCommitTogether() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            tx.executeWithoutResult(() -> {
                widgets.save(widget("outer"));
                tx.executeWithoutResult(() -> widgets.save(widget("inner")));
            });

            assertThat(widgets.count()).isEqualTo(2);
        }
    }

    @Test
    void outerFailureRollsBackInnerWrites() {
        try (PolyDB db = start()) {
            CrudRepository<Widget, Object> widgets = db.repository(Widget.class);
            TransactionTemplate tx = new TransactionTemplate(db.getDataSource());

            assertThatThrownBy(() -> tx.executeWithoutResult(() -> {
                widgets.save(widget("outer"));
                tx.executeWithoutResult(() -> widgets.save(widget("inner")));
                throw new RuntimeException("boom");
            })).hasMessageContaining("boom");

            assertThat(widgets.count()).isZero();
        }
    }
}
