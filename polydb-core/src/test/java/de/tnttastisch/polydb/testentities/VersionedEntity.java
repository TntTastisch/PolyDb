package de.tnttastisch.polydb.testentities;

import de.tnttastisch.polydb.core.annotations.Column;
import de.tnttastisch.polydb.core.annotations.Entity;
import de.tnttastisch.polydb.core.annotations.Id;
import de.tnttastisch.polydb.core.annotations.Table;
import de.tnttastisch.polydb.core.annotations.Version;

import java.util.UUID;

/** Test fixture for optimistic locking: {@code version} is initialised on insert and bumped on update. */
@Entity
@Table(name = "versioned")
public class VersionedEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public VersionedEntity() {
    }

    public VersionedEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getVersion() {
        return version;
    }
}
