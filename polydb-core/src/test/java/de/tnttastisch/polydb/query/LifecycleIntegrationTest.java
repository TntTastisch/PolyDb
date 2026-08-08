package de.tnttastisch.polydb.query;

import de.tnttastisch.polydb.PolyDB;
import de.tnttastisch.polydb.core.exception.OptimisticLockException;
import de.tnttastisch.polydb.testentities.AuditedEntity;
import de.tnttastisch.polydb.testentities.SoftDeletableEntity;
import de.tnttastisch.polydb.testentities.VersionedEntity;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage of the entity-lifecycle features against H2: auditing (dates and authors),
 * optimistic locking ({@code @Version} init/bump and concurrent-update detection), soft deletion
 * (flag-and-hide) and domain events fired after save/delete.
 */
class LifecycleIntegrationTest {

    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:life_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    @Test
    void auditingFillsDatesAndAuthors() {
        try (PolyDB db = start()) {
            AuditingContext.setAuditor("alice");
            try {
                CrudRepository<AuditedEntity, Object> repo = db.repository(AuditedEntity.class);
                AuditedEntity saved = repo.save(new AuditedEntity(UUID.randomUUID(), "first"));

                assertThat(saved.getCreatedAt()).isNotNull();
                assertThat(saved.getUpdatedAt()).isNotNull();
                assertThat(saved.getCreatedBy()).isEqualTo("alice");
                assertThat(saved.getUpdatedBy()).isEqualTo("alice");

                AuditingContext.setAuditor("bob");
                saved.setName("second");
                repo.save(saved);

                AuditedEntity reread = repo.findById(saved.getId()).orElseThrow();
                assertThat(reread.getCreatedBy()).isEqualTo("alice"); // unchanged on update
                assertThat(reread.getUpdatedBy()).isEqualTo("bob");   // refreshed on update
            } finally {
                AuditingContext.clear();
            }
        }
    }

    @Test
    void versionInitialisedAndBumped() {
        try (PolyDB db = start()) {
            CrudRepository<VersionedEntity, Object> repo = db.repository(VersionedEntity.class);
            VersionedEntity saved = repo.save(new VersionedEntity(UUID.randomUUID(), "n"));
            assertThat(saved.getVersion()).isZero();

            saved.setName("n2");
            repo.save(saved);
            assertThat(saved.getVersion()).isEqualTo(1);
            assertThat(repo.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(1);
        }
    }

    @Test
    void concurrentUpdateRaisesOptimisticLock() {
        try (PolyDB db = start()) {
            CrudRepository<VersionedEntity, Object> repo = db.repository(VersionedEntity.class);
            VersionedEntity saved = repo.save(new VersionedEntity(UUID.randomUUID(), "n"));

            VersionedEntity fresh = repo.findById(saved.getId()).orElseThrow();
            VersionedEntity stale = repo.findById(saved.getId()).orElseThrow();

            fresh.setName("fresh");
            repo.save(fresh); // version 0 -> 1

            stale.setName("stale"); // still version 0
            assertThatThrownBy(() -> repo.save(stale)).isInstanceOf(OptimisticLockException.class);
        }
    }

    @Test
    void softDeleteHidesRowButKeepsIt() throws Exception {
        try (PolyDB db = start()) {
            CrudRepository<SoftDeletableEntity, Object> repo = db.repository(SoftDeletableEntity.class);
            SoftDeletableEntity saved = repo.save(new SoftDeletableEntity(UUID.randomUUID(), "n"));

            repo.delete(saved);

            assertThat(repo.findById(saved.getId())).isEmpty();
            assertThat(repo.findAll()).isEmpty();
            assertThat(repo.count()).isZero();
            // The row is still physically present — it was flagged, not removed.
            assertThat(rawCount(db, "soft_deletable")).isEqualTo(1);
        }
    }

    @Test
    void eventsFireAfterSaveAndDelete() {
        try (PolyDB db = start()) {
            List<String> events = new ArrayList<>();
            EntityListener listener = new EntityListener() {
                @Override
                public void afterSave(Object entity) {
                    events.add("save");
                }

                @Override
                public void afterDelete(Object entity) {
                    events.add("delete");
                }
            };
            EntityEvents.addListener(listener);
            try {
                CrudRepository<VersionedEntity, Object> repo = db.repository(VersionedEntity.class);
                VersionedEntity saved = repo.save(new VersionedEntity(UUID.randomUUID(), "n"));
                repo.delete(saved);
                assertThat(events).containsExactly("save", "delete");
            } finally {
                EntityEvents.removeListener(listener);
            }
        }
    }

    private static long rawCount(PolyDB db, String table) throws Exception {
        try (Connection connection = db.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
