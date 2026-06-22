package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Index;
import de.tnttastisch.polydb.core.annotations.JoinColumn;
import de.tnttastisch.polydb.core.annotations.OneToOne;
import de.tnttastisch.polydb.core.exception.PolyDBException;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link EntityParser} edge cases around relations and schema validation, using small inline
 * fixtures: an owning one-to-one mapping and an invalid class-level index declaration.
 */
class RelationEdgeCaseParserTest {

    private final EntityParser parser = new EntityParser();

    /**
     * An owning {@code @OneToOne} produces a foreign-key column ({@code account_id}) that is also
     * marked {@code unique}, since the single-valued cardinality of one-to-one must be enforced at the
     * column level.
     */
    @Test
    void owningOneToOneForeignKeyColumnIsUnique() {
        EntityModel model = parser.parseEntity(Profile.class);

        FieldModel fk = model.getFields().stream()
                .filter(FieldModel::isForeignKey)
                .findFirst()
                .orElseThrow();
        assertThat(fk.getColumnName()).isEqualTo("account_id");
        assertThat(fk.isUnique()).isTrue(); // enforces one-to-one cardinality
    }

    /**
     * A class-level {@code @Index} that names no columns is invalid: parsing fails with a
     * {@link PolyDBException} complaining that at least one column is required.
     */
    @Test
    void classLevelIndexWithoutColumnsIsRejected() {
        assertThatThrownBy(() -> parser.parseEntity(BadlyIndexed.class))
                .isInstanceOf(PolyDBException.class)
                .hasMessageContaining("at least one column");
    }

    /** Referenced side of the one-to-one fixture. */
    @Entity
    static class Account {
        @Id
        @Column(name = "id")
        private UUID id;
    }

    /** Owning side of the one-to-one fixture, holding the unique {@code account_id} foreign key. */
    @Entity
    static class Profile {
        @Id
        @Column(name = "id")
        private UUID id;

        @OneToOne
        @JoinColumn(name = "account_id")
        private Account account;
    }

    /** Invalid fixture: a named class-level index that declares no columns. */
    @Entity
    @Index(name = "broken")
    static class BadlyIndexed {
        @Id
        @Column(name = "id")
        private UUID id;
    }
}
