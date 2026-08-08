package de.tnttastisch.polydb.migration;

import de.tnttastisch.polydb.migration.operation.AddColumnOperation;
import de.tnttastisch.polydb.migration.operation.CreateTableOperation;
import de.tnttastisch.polydb.migration.operation.DropColumnOperation;
import de.tnttastisch.polydb.migration.operation.DropTableOperation;
import de.tnttastisch.polydb.migration.operation.IrreversibleOperationException;
import de.tnttastisch.polydb.migration.operation.MigrationOperation;
import de.tnttastisch.polydb.migration.operation.RenameTableOperation;
import de.tnttastisch.polydb.schema.model.FieldModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the automatic-rollback ({@code reverse()}) semantics of migration operations. */
class MigrationOperationReverseTest {

    @Test
    void createTableReversesToDropTableThatRestoresTheCreate() {
        CreateTableOperation create = new CreateTableOperation("t", List.of(field("id")));

        MigrationOperation reverse = create.reverse();

        assertThat(reverse).isInstanceOf(DropTableOperation.class);
        assertThat(((DropTableOperation) reverse).tableName()).isEqualTo("t");
        assertThat(reverse.isReversible()).isTrue();
        assertThat(reverse.reverse()).isSameAs(create);
    }

    @Test
    void addColumnReversesToDropColumnThatCanRestore() {
        AddColumnOperation add = new AddColumnOperation("users", field("email"));

        MigrationOperation drop = add.reverse();

        assertThat(drop).isInstanceOf(DropColumnOperation.class);
        assertThat(drop.isReversible()).isTrue();
        assertThat(drop.reverse()).isInstanceOf(AddColumnOperation.class);
    }

    @Test
    void plainDropTableIsIrreversible() {
        DropTableOperation drop = new DropTableOperation("gone");

        assertThat(drop.isReversible()).isFalse();
        assertThatThrownBy(drop::reverse).isInstanceOf(IrreversibleOperationException.class);
    }

    @Test
    void renameTableIsSymmetric() {
        RenameTableOperation rename = new RenameTableOperation("old_name", "new_name");

        MigrationOperation reverse = rename.reverse();

        assertThat(reverse).isInstanceOf(RenameTableOperation.class);
        assertThat(((RenameTableOperation) reverse).fromName()).isEqualTo("new_name");
        assertThat(((RenameTableOperation) reverse).toName()).isEqualTo("old_name");
    }

    private FieldModel field(String name) {
        return new FieldModel(null, name, String.class, false, false, true, false, 255, 0, 0);
    }
}
