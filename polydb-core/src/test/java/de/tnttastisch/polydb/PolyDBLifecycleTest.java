package de.tnttastisch.polydb;

import de.tnttastisch.polydb.testentities.Author;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the lifecycle contract of the {@link PolyDB} facade: how it behaves once
 * {@link PolyDB#close()} has been called. Each test boots a fresh, uniquely named in-memory H2
 * instance (with auto-migration enabled) so the cases are fully isolated.
 */
class PolyDBLifecycleTest {

    /** Starts an isolated in-memory H2-backed PolyDB instance with the test entity package scanned. */
    private PolyDB start() {
        return PolyDB.builder()
                .url("jdbc:h2:mem:life_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .entityPackage("de.tnttastisch.polydb.testentities")
                .autoMigration(true)
                .start();
    }

    /**
     * After {@link PolyDB#close()} the instance reports itself closed and any attempt to obtain a
     * repository fails fast with an {@link IllegalStateException} mentioning the closed state.
     */
    @Test
    void repositoryAccessAfterCloseThrowsIllegalState() {
        PolyDB db = start();
        db.close();

        assertThat(db.isClosed()).isTrue();
        assertThatThrownBy(() -> db.repository(Author.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    /**
     * Closing is idempotent and {@link PolyDB#shutdown()} is just an alias for
     * {@link PolyDB#close()}: repeated invocations of either must be silent no-ops rather than
     * throwing, and the instance stays in the closed state.
     */
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
