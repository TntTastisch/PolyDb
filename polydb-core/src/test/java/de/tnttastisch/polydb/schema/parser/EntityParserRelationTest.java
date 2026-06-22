package de.tnttastisch.polydb.schema.parser;

import de.tnttastisch.polydb.core.annotations.FetchType;
import de.tnttastisch.polydb.schema.model.EntityModel;
import de.tnttastisch.polydb.schema.model.FieldModel;
import de.tnttastisch.polydb.schema.model.RelationModel;
import de.tnttastisch.polydb.schema.model.RelationType;
import de.tnttastisch.polydb.testentities.Author;
import de.tnttastisch.polydb.testentities.Book;
import de.tnttastisch.polydb.testentities.Gadget;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityParserRelationTest {

    private final EntityParser parser = new EntityParser();

    @Test
    void idColumnStaysNotNullableEvenWithColumnAnnotation() {
        // Author declares @Id @Column(name = "id"); @Column.nullable() defaults to true and must
        // not relax the primary key.
        EntityModel model = parser.parseEntity(Author.class);

        FieldModel idColumn = model.getFields().stream()
                .filter(FieldModel::isId)
                .findFirst()
                .orElseThrow();
        assertThat(idColumn.isNullable()).isFalse();
    }

    @Test
    void skipsStaticTransientAndTransientAnnotatedFields() {
        EntityModel model = parser.parseEntity(Gadget.class);

        assertThat(model.getFields())
                .extracting(FieldModel::getColumnName)
                .containsExactlyInAnyOrder("id", "name");
        assertThat(model.getRelations()).isEmpty();
    }

    @Test
    void owningManyToOneCreatesForeignKeyColumnWithTargetIdType() {
        EntityModel model = parser.parseEntity(Book.class);

        FieldModel fkColumn = model.getFields().stream()
                .filter(FieldModel::isForeignKey)
                .findFirst()
                .orElseThrow();

        assertThat(fkColumn.getColumnName()).isEqualTo("author_id");
        assertThat(fkColumn.getType()).isEqualTo(UUID.class);
        assertThat(fkColumn.isNullable()).isFalse(); // optional = false, @JoinColumn(nullable = false)

        assertThat(model.getRelations()).hasSize(1);
        RelationModel relation = model.getRelations().get(0);
        assertThat(relation.getType()).isEqualTo(RelationType.MANY_TO_ONE);
        assertThat(relation.isOwningSide()).isTrue();
        assertThat(relation.getJoinColumnName()).isEqualTo("author_id");
        assertThat(relation.getReferencedColumnName()).isEqualTo("id");
        assertThat(relation.getReferencedTable()).isEqualTo("authors");
        assertThat(relation.getTargetEntity()).isEqualTo(Author.class);
        assertThat(relation.getFetch()).isEqualTo(FetchType.EAGER);
    }

    @Test
    void inverseOneToManyHasNoColumnAndIsLazyByDefault() {
        EntityModel model = parser.parseEntity(Author.class);

        assertThat(model.getFields())
                .extracting(FieldModel::getColumnName)
                .containsExactlyInAnyOrder("id", "name");

        assertThat(model.getRelations()).hasSize(1);
        RelationModel relation = model.getRelations().get(0);
        assertThat(relation.getType()).isEqualTo(RelationType.ONE_TO_MANY);
        assertThat(relation.isOwningSide()).isFalse();
        assertThat(relation.getMappedBy()).isEqualTo("author");
        assertThat(relation.getTargetEntity()).isEqualTo(Book.class);
        assertThat(relation.getFetch()).isEqualTo(FetchType.LAZY);
    }
}
