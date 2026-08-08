package de.tnttastisch.polydb.query.support;

import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.query.CrudRepository;
import de.tnttastisch.polydb.testentities.Widget;
import de.tnttastisch.polydb.testrepositories.DeepWidgetRepository;
import de.tnttastisch.polydb.testrepositories.WidgetRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RepositoryMetadata}: it resolves the entity and id types from a repository
 * interface's generic declaration, following the hierarchy through any number of intermediate
 * interfaces, and rejects inputs that are not {@link de.tnttastisch.polydb.query.Repository} interfaces.
 */
class RepositoryMetadataTest {

    @Test
    void resolvesEntityAndIdFromDirectExtension() {
        RepositoryMetadata metadata = RepositoryMetadata.of(WidgetRepository.class);
        assertThat(metadata.getEntityType()).isEqualTo(Widget.class);
        assertThat(metadata.getIdType()).isEqualTo(UUID.class);
        assertThat(metadata.getRepositoryInterface()).isEqualTo(WidgetRepository.class);
    }

    @Test
    void resolvesThroughMultipleInheritanceLevels() {
        RepositoryMetadata metadata = RepositoryMetadata.of(DeepWidgetRepository.class);
        assertThat(metadata.getEntityType()).isEqualTo(Widget.class);
        assertThat(metadata.getIdType()).isEqualTo(UUID.class);
    }

    @Test
    void rejectsNonInterface() {
        assertThatThrownBy(() -> RepositoryMetadata.of(Widget.class))
                .isInstanceOf(PolyDBException.class)
                .hasMessageContaining("interface");
    }

    @Test
    void rejectsInterfaceThatDoesNotExtendRepository() {
        assertThatThrownBy(() -> RepositoryMetadata.of(Runnable.class))
                .isInstanceOf(PolyDBException.class)
                .hasMessageContaining("must extend");
    }

    @Test
    void rejectsRawRepositoryWithoutTypeArguments() {
        // CrudRepository itself leaves T and ID unbound, so there is nothing concrete to resolve.
        assertThatThrownBy(() -> RepositoryMetadata.of(CrudRepository.class))
                .isInstanceOf(PolyDBException.class);
    }
}
