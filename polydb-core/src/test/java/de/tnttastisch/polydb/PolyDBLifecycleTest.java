package de.tnttastisch.polydb;

import de.tnttastisch.polydb.testentities.Author;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class PolyDBLifecycleTest {

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
    void repositoryAccessAfterCloseThrowsIllegalState() {
        PolyDB db = start();
        db.close();

        assertThat(db.isClosed()).isTrue();
        assertThatThrownBy(() -> db.repository(Author.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void closeIsIdempotentAndShutdownIsAnAlias() {
        PolyDB db = start();

        db.close();
        // Subsequent close()/shutdown() calls must be no-ops, not errors.
        assertThat(catchThrowable(db::close)).isNull();
        assertThat(catchThrowable(db::shutdown)).isNull();
        assertThat(db.isClosed()).isTrue();
    }
}
