package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.SoftDelete;
import de.tnttastisch.polydb.core.annotations.Table;

import java.util.UUID;

/** Test fixture for soft deletion: {@code delete} flags the row and reads hide flagged rows. */
@Entity
@Table(name = "soft_deletable")
public class SoftDeletableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @SoftDelete
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public SoftDeletableEntity() {
    }

    public SoftDeletableEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
