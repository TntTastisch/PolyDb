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

class RelationEdgeCaseParserTest {

    private final EntityParser parser = new EntityParser();

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

    @Test
    void classLevelIndexWithoutColumnsIsRejected() {
        assertThatThrownBy(() -> parser.parseEntity(BadlyIndexed.class))
                .isInstanceOf(PolyDBException.class)
                .hasMessageContaining("at least one column");
    }

    @Entity
    static class Account {
        @Id
        @Column(name = "id")
        private UUID id;
    }

    @Entity
    static class Profile {
        @Id
        @Column(name = "id")
        private UUID id;

        @OneToOne
        @JoinColumn(name = "account_id")
        private Account account;
    }

    @Entity
    @Index(name = "broken")
    static class BadlyIndexed {
        @Id
        @Column(name = "id")
        private UUID id;
    }
}
